package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NationalityPageBoundary;
import com.alexastudillo.partyregistry.application.model.NationalityPagePosition;
import com.alexastudillo.partyregistry.application.model.NationalitySearchScope;
import com.alexastudillo.partyregistry.application.port.NationalityCursorPort;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
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

/** Signs exact nationality tuple continuations in a domain distinct from root Party cursors. */
@ApplicationScoped
public class HmacNationalityCursorAdapter implements NationalityCursorPort {

    private static final String FORMAT_VERSION = "1";
    private static final int DIGEST_BYTES = 32;
    private static final int PAYLOAD_BYTES = 65;
    private static final int MAXIMUM_TOKEN_LENGTH = 256;
    private static final byte[] MAC_CONTEXT = "nationality-list-cursor-v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final PartyCursorKeyMaterial keyMaterial;

    @Inject
    public HmacNationalityCursorAdapter(PartyCursorKeyMaterial keyMaterial) {
        this.keyMaterial = Objects.requireNonNull(keyMaterial, "keyMaterial");
    }

    @Override
    public String encode(NationalityPageBoundary boundary, NationalitySearchScope scope) {
        Objects.requireNonNull(boundary, "boundary");
        Objects.requireNonNull(scope, "scope");
        UUID id = boundary.position().nationalityId().value();
        Instant createdAt = boundary.position().createdAt();
        ByteBuffer payload = ByteBuffer.allocate(PAYLOAD_BYTES);
        payload.put((byte) (boundary.direction() == NationalityPageBoundary.Direction.NEXT ? 0 : 1));
        payload.putLong(createdAt.getEpochSecond()).putInt(createdAt.getNano());
        payload.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
        payload.putInt(scope.criteria().limit()).put(scopeDigest(scope));
        String signed = FORMAT_VERSION + "." + keyMaterial.currentKeyId() + "." + ENCODER.encodeToString(payload.array());
        return signed + "." + ENCODER.encodeToString(authenticate(signed, keyMaterial.currentKey()));
    }

    @Override
    public NationalityPageBoundary decode(String token, NationalitySearchScope expectedScope) {
        Objects.requireNonNull(expectedScope, "expectedScope");
        String[] parts = tokenParts(token);
        SecretKey key = keyMaterial.verificationKey(parts[1]).orElseThrow(HmacNationalityCursorAdapter::invalidCursor);
        byte[] payload = decodeCanonical(parts[2], PAYLOAD_BYTES);
        byte[] signature = decodeCanonical(parts[3], DIGEST_BYTES);
        String signed = parts[0] + "." + parts[1] + "." + parts[2];
        if (!MessageDigest.isEqual(authenticate(signed, key), signature)) {
            throw invalidCursor();
        }
        ByteBuffer encoded = ByteBuffer.wrap(payload);
        NationalityPageBoundary.Direction direction = switch (encoded.get()) {
            case 0 -> NationalityPageBoundary.Direction.NEXT;
            case 1 -> NationalityPageBoundary.Direction.PREVIOUS;
            default -> throw invalidCursor();
        };
        long seconds = encoded.getLong();
        int nanos = encoded.getInt();
        UUID id = new UUID(encoded.getLong(), encoded.getLong());
        int limit = encoded.getInt();
        byte[] scopeHash = new byte[DIGEST_BYTES];
        encoded.get(scopeHash);
        if (nanos < 0 || nanos > 999_999_999 || limit != expectedScope.criteria().limit()
                || !MessageDigest.isEqual(scopeHash, scopeDigest(expectedScope))) {
            throw invalidCursor();
        }
        try {
            return new NationalityPageBoundary(direction,
                    new NationalityPagePosition(Instant.ofEpochSecond(seconds, nanos), new NationalityId(id)));
        } catch (DateTimeException _) {
            throw invalidCursor();
        }
    }

    private static String[] tokenParts(@Nullable String token) {
        if (token == null || token.length() > MAXIMUM_TOKEN_LENGTH || token.isBlank()) {
            throw invalidCursor();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 4 || !FORMAT_VERSION.equals(parts[0]) || !parts[1].matches("[A-Za-z0-9_-]{1,64}")) {
            throw invalidCursor();
        }
        return parts;
    }

    private static byte[] decodeCanonical(String value, int expectedLength) {
        try {
            byte[] decoded = DECODER.decode(value);
            if (decoded.length != expectedLength || !ENCODER.encodeToString(decoded).equals(value)) {
                throw invalidCursor();
            }
            return decoded;
        } catch (IllegalArgumentException _) {
            throw invalidCursor();
        }
    }

    private static byte[] scopeDigest(NationalitySearchScope scope) {
        try (var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes)) {
            output.writeUTF("nationality-list-scope-v1");
            writeUuid(output, scope.tenantId().value());
            writeUuid(output, scope.partyId().value());
            var criteria = scope.criteria();
            String code = criteria.countryCode();
            output.writeBoolean(code != null);
            if (code != null) {
                output.write(code.getBytes(StandardCharsets.US_ASCII));
            }
            output.writeByte(primaryScopeTag(criteria.isPrimary()));
            output.writeLong(criteria.asOfDate().toEpochDay());
            output.writeBoolean(criteria.includeExpired());
            output.writeInt(criteria.limit());
            output.flush();
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
        } catch (IOException | GeneralSecurityException _) {
            throw new IllegalStateException("Nationality cursor scope encoding failed");
        }
    }

    private static int primaryScopeTag(@Nullable Boolean isPrimary) {
        if (isPrimary == null) {
            return 0;
        }
        return Boolean.TRUE.equals(isPrimary) ? 1 : 2;
    }

    private static void writeUuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static byte[] authenticate(String signed, SecretKey key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(MAC_CONTEXT);
            return mac.doFinal(signed.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException _) {
            throw new IllegalStateException("Nationality cursor authentication failed");
        }
    }

    private static ApplicationException invalidCursor() {
        return new ApplicationException(new ApplicationFailure.InvalidNationalityCursor());
    }
}
