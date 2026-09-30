package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyPagePosition;
import com.alexastudillo.partyregistry.application.model.PartySearchScope;
import com.alexastudillo.partyregistry.application.port.PartyCursorPort;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
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

/**
 * Authenticates versioned exact Party continuations using independent HMAC keys and bounded in-memory codecs.
 */
@ApplicationScoped
public class HmacPartyCursorAdapter implements PartyCursorPort {

    private static final String FORMAT_VERSION = "1";
    private static final int PAYLOAD_BYTES = 65;
    private static final int DIGEST_BYTES = 32;
    private static final int MAXIMUM_TOKEN_LENGTH = 256;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final byte[] MAC_CONTEXT = "party-list-cursor-v1\0".getBytes(StandardCharsets.US_ASCII);

    private final PartyCursorKeyMaterial keyMaterial;

    /** Creates a cursor adapter using the eagerly validated current and retained key ring. */
    @Inject
    public HmacPartyCursorAdapter(PartyCursorKeyMaterial keyMaterial) {
        this.keyMaterial = Objects.requireNonNull(keyMaterial, "keyMaterial");
    }

    @Override
    public String encode(PartyPageBoundary boundary, PartySearchScope scope) {
        Objects.requireNonNull(boundary, "boundary");
        Objects.requireNonNull(scope, "scope");
        var position = boundary.position();
        UUID id = position.partyId().value();
        ByteBuffer payload = ByteBuffer.allocate(PAYLOAD_BYTES);
        payload.put((byte) (boundary.direction() == PartyPageBoundary.Direction.NEXT ? 0 : 1));
        payload.putLong(position.createdAt().getEpochSecond()).putInt(position.createdAt().getNano());
        payload.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
        payload.putInt(scope.criteria().limit()).put(scopeDigest(scope));
        String signed = FORMAT_VERSION + "." + keyMaterial.currentKeyId() + "." + ENCODER.encodeToString(payload.array());
        return signed + "." + ENCODER.encodeToString(authenticate(signed, keyMaterial.currentKey()));
    }

    @Override
    public PartyPageBoundary decode(String token, PartySearchScope expectedScope) {
        Objects.requireNonNull(expectedScope, "expectedScope");
        String[] parts = tokenParts(token);
        SecretKey key = keyMaterial.verificationKey(parts[1]).orElseThrow(HmacPartyCursorAdapter::invalidCursor);
        byte[] payload = decodeCanonical(parts[2], PAYLOAD_BYTES);
        byte[] signature = decodeCanonical(parts[3], DIGEST_BYTES);
        String signed = parts[0] + "." + parts[1] + "." + parts[2];
        if (!MessageDigest.isEqual(authenticate(signed, key), signature)) {
            throw invalidCursor();
        }
        return readBoundary(payload, expectedScope);
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

    private static byte[] decodeCanonical(String encoded, int expectedLength) {
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

    private static PartyPageBoundary readBoundary(byte[] encoded, PartySearchScope scope) {
        ByteBuffer payload = ByteBuffer.wrap(encoded);
        PartyPageBoundary.Direction direction = switch (payload.get()) {
            case 0 -> PartyPageBoundary.Direction.NEXT;
            case 1 -> PartyPageBoundary.Direction.PREVIOUS;
            default -> throw invalidCursor();
        };
        long seconds = payload.getLong();
        int nanos = payload.getInt();
        UUID id = new UUID(payload.getLong(), payload.getLong());
        int limit = payload.getInt();
        byte[] digest = new byte[DIGEST_BYTES];
        payload.get(digest);
        if (nanos < 0 || nanos > 999999999 || limit != scope.criteria().limit()
                || !MessageDigest.isEqual(digest, scopeDigest(scope))) {
            throw invalidCursor();
        }
        try {
            return new PartyPageBoundary(direction, new PartyPagePosition(Instant.ofEpochSecond(seconds, nanos), new PartyId(id)));
        } catch (DateTimeException _) {
            throw invalidCursor();
        }
    }

    private static byte[] authenticate(String signed, SecretKey key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(MAC_CONTEXT);
            return mac.doFinal(signed.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException _) {
            throw new IllegalStateException("Party cursor authentication failed");
        }
    }

    private static byte[] scopeDigest(PartySearchScope scope) {
        try (var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes)) {
            output.writeUTF("party-list-scope-v1");
            UUID tenant = scope.tenantId().value();
            output.writeLong(tenant.getMostSignificantBits());
            output.writeLong(tenant.getLeastSignificantBits());
            var criteria = scope.criteria();
            PartyType type = criteria.type();
            PartyRecordStatus status = criteria.recordStatus();
            writeText(output, type == null ? null : type.name());
            writeText(output, status == null ? null : status.name());
            writeText(output, criteria.name().startsWith());
            writeText(output, criteria.name().contains());
            writeInstant(output, criteria.createdFrom());
            writeInstant(output, criteria.createdTo());
            output.writeInt(criteria.limit());
            output.flush();
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
        } catch (IOException | GeneralSecurityException _) {
            throw new IllegalStateException("Party cursor scope encoding failed");
        }
    }

    private static void writeText(DataOutputStream output, @Nullable String text) throws IOException {
        output.writeInt(text == null ? -1 : text.length());
        if (text != null) {
            // UTF-16 units preserve distinct Java strings, including unpaired input surrogates, without replacement.
            output.writeChars(text);
        }
    }

    private static void writeInstant(DataOutputStream output, @Nullable Instant instant) throws IOException {
        output.writeBoolean(instant != null);
        if (instant != null) {
            output.writeLong(instant.getEpochSecond());
            output.writeInt(instant.getNano());
        }
    }

    private static ApplicationException invalidCursor() {
        return new ApplicationException(new ApplicationFailure.InvalidPartyCursor());
    }
}
