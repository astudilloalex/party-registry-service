package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePagePosition;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchScope;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeCursorPort;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/** Authenticates bounded scheme-list continuations with provisioned rotation keys and an isolated resource domain. */
@ApplicationScoped
public class HmacIdentifierSchemeCursorAdapter implements IdentifierSchemeCursorPort {
    private static final String FORMAT_VERSION = "1";
    private static final int PAYLOAD_BYTES = 65;
    private static final int DIGEST_BYTES = 32;
    private static final int MAXIMUM_TOKEN_LENGTH = 256;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final byte[] MAC_CONTEXT = "identifier-scheme-list-cursor-v1\0".getBytes(StandardCharsets.US_ASCII);

    private final PartyCursorKeyMaterial keyMaterial;

    /** Reuses the eagerly validated current signing key and retained verification keys without new configuration. */
    @Inject
    public HmacIdentifierSchemeCursorAdapter(PartyCursorKeyMaterial keyMaterial) {
        this.keyMaterial = Objects.requireNonNull(keyMaterial, "keyMaterial");
    }

    /** Signs the exact tuple and complete scope; rejects submicrosecond positions instead of rounding database boundaries. */
    @Override
    public String encode(IdentifierSchemePageBoundary boundary, IdentifierSchemeSearchScope scope) {
        Objects.requireNonNull(boundary, "boundary");
        Objects.requireNonNull(scope, "scope");
        var position = boundary.position();
        if (position.createdAt().getNano() % 1_000 != 0) {
            throw new IllegalArgumentException("Identifier scheme cursor position must have microsecond precision");
        }
        UUID id = position.schemeId().value();
        ByteBuffer payload = ByteBuffer.allocate(PAYLOAD_BYTES);
        payload.put((byte) (boundary.direction() == IdentifierSchemePageBoundary.Direction.NEXT ? 0 : 1));
        payload.putLong(position.createdAt().getEpochSecond()).putInt(position.createdAt().getNano());
        payload.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
        payload.putInt(scope.criteria().limit()).put(scopeDigest(scope));
        String signed = FORMAT_VERSION + "." + keyMaterial.currentKeyId() + "." + ENCODER.encodeToString(payload.array());
        return signed + "." + ENCODER.encodeToString(authenticate(signed, keyMaterial.currentKey()));
    }

    /** Rejects noncanonical, altered, foreign-resource, or cross-scope tokens with a sanitized neutral cursor failure. */
    @Override
    public IdentifierSchemePageBoundary decode(@Nullable String token, IdentifierSchemeSearchScope expectedScope) {
        Objects.requireNonNull(expectedScope, "expectedScope");
        String[] parts = tokenParts(token);
        SecretKey key = keyMaterial.verificationKey(parts[1]).orElseThrow(HmacIdentifierSchemeCursorAdapter::invalidCursor);
        byte[] payload = decodeCanonical(parts[2], PAYLOAD_BYTES);
        byte[] signature = decodeCanonical(parts[3], DIGEST_BYTES);
        String signed = parts[0] + "." + parts[1] + "." + parts[2];
        if (!MessageDigest.isEqual(authenticate(signed, key), signature)) {
            throw invalidCursor();
        }
        return readBoundary(payload, expectedScope);
    }

    private static String[] tokenParts(@Nullable String token) {
        // Check the total bound before splitting or decoding attacker-controlled input.
        if (token == null || token.length() > MAXIMUM_TOKEN_LENGTH || token.isBlank()) {
            throw invalidCursor();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 4 || !FORMAT_VERSION.equals(parts[0]) || !parts[1].matches("[A-Za-z0-9_-]{1,64}")) {
            throw invalidCursor();
        }
        return parts;
    }

    private static byte[] decodeCanonical(String encoded, int expectedLength) {
        if (encoded.length() != (expectedLength * 8 + 5) / 6) {
            throw invalidCursor();
        }
        try {
            byte[] decoded = DECODER.decode(encoded);
            if (decoded.length != expectedLength || !ENCODER.encodeToString(decoded).equals(encoded)) {
                throw invalidCursor();
            }
            return decoded;
        } catch (IllegalArgumentException _) {
            throw invalidCursor();
        }
    }

    private static IdentifierSchemePageBoundary readBoundary(byte[] encoded, IdentifierSchemeSearchScope scope) {
        ByteBuffer payload = ByteBuffer.wrap(encoded);
        IdentifierSchemePageBoundary.Direction direction = switch (payload.get()) {
            case 0 -> IdentifierSchemePageBoundary.Direction.NEXT;
            case 1 -> IdentifierSchemePageBoundary.Direction.PREVIOUS;
            default -> throw invalidCursor();
        };
        long seconds = payload.getLong();
        int nanos = payload.getInt();
        UUID id = new UUID(payload.getLong(), payload.getLong());
        int limit = payload.getInt();
        byte[] digest = new byte[DIGEST_BYTES];
        payload.get(digest);
        if (nanos < 0 || nanos > 999_999_999 || nanos % 1_000 != 0 || limit != scope.criteria().limit()
                || !MessageDigest.isEqual(digest, scopeDigest(scope))) {
            throw invalidCursor();
        }
        try {
            return new IdentifierSchemePageBoundary(direction,
                    new IdentifierSchemePagePosition(Instant.ofEpochSecond(seconds, nanos), new IdentifierSchemeId(id)));
        } catch (DateTimeException _) {
            throw invalidCursor();
        }
    }

    private static byte[] scopeDigest(IdentifierSchemeSearchScope scope) {
        try (var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes)) {
            output.writeUTF("identifier-scheme-list-scope-v1");
            UUID tenant = scope.tenantId().value();
            output.writeLong(tenant.getMostSignificantBits());
            output.writeLong(tenant.getLeastSignificantBits());
            var criteria = scope.criteria();
            writeText(output, criteria.issuingCountryCode());
            writeEnum(output, criteria.category());
            writeEnum(output, criteria.applicableSubjectType());
            writeEnum(output, criteria.status());
            output.writeInt(criteria.limit());
            output.flush();
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
        } catch (IOException | GeneralSecurityException _) {
            throw new IllegalStateException("Identifier scheme cursor scope encoding failed");
        }
    }

    private static void writeEnum(DataOutputStream output, @Nullable Enum<?> value) throws IOException {
        writeText(output, value == null ? null : value.name());
    }

    private static void writeText(DataOutputStream output, @Nullable String text) throws IOException {
        output.writeInt(text == null ? -1 : text.length());
        if (text != null) {
            // Length-delimited exact text distinguishes absent filters without normalization or delimiter ambiguity.
            output.writeChars(text);
        }
    }

    private static byte[] authenticate(String signed, SecretKey key) {
        try {
            // Mac is mutable: each bounded synchronous operation owns its instance, including concurrent CDI calls.
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(MAC_CONTEXT);
            return mac.doFinal(signed.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException _) {
            throw new IllegalStateException("Identifier scheme cursor authentication failed");
        }
    }

    private static ApplicationException invalidCursor() {
        return new ApplicationException(new ApplicationFailure.InvalidIdentifierSchemeCursor());
    }
}
