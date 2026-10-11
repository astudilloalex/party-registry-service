package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierSearchScope;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPagePosition;
import com.alexastudillo.partyregistry.application.port.IdentifierSearchCursorPort;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Authenticates bounded prefix-search keysets and keyed sensitive scopes using retained pagination signing keys. */
@ApplicationScoped
public class HmacIdentifierSearchCursorAdapter implements IdentifierSearchCursorPort {
    private static final int PAYLOAD_BYTES = 65;
    private static final int DIGEST_BYTES = 32;
    private static final byte[] TOKEN_CONTEXT = "party-identifier-prefix-search-cursor-v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SCOPE_CONTEXT = "party-identifier-prefix-search-scope-v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final PartyCursorKeyMaterial keys;

    @Inject
    public HmacIdentifierSearchCursorAdapter(PartyCursorKeyMaterial keys) {
        this.keys = Objects.requireNonNull(keys, "keys");
    }

    @Override
    public String encode(IdentifierSearchScope scope, PartyIdentifierPageBoundary boundary) {
        var position = boundary.position();
        UUID id = position.identifierId().value();
        byte[] payload = ByteBuffer.allocate(PAYLOAD_BYTES)
                .put((byte) (boundary.direction() == PartyIdentifierPageBoundary.Direction.NEXT ? 0 : 1))
                .putLong(position.createdAt().getEpochSecond()).putInt(position.createdAt().getNano())
                .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits())
                .putInt(scope.limit()).put(scopeDigest(scope, keys.currentKey())).array();
        String signed = "1." + keys.currentKeyId() + "." + ENCODER.encodeToString(payload);
        return signed + "." + ENCODER.encodeToString(authenticate(signed, keys.currentKey()));
    }

    @Override
    public PartyIdentifierPageBoundary decode(IdentifierSearchScope scope, String token) {
        String[] parts = tokenParts(token);
        SecretKey key = keys.verificationKey(parts[1]).orElseThrow(HmacIdentifierSearchCursorAdapter::invalidCursor);
        byte[] payload = decodeCanonical(parts[2], PAYLOAD_BYTES);
        byte[] signature = decodeCanonical(parts[3], DIGEST_BYTES);
        String signed = parts[0] + "." + parts[1] + "." + parts[2];
        if (!MessageDigest.isEqual(authenticate(signed, key), signature)) {
            throw invalidCursor();
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        var direction = switch (buffer.get()) {
            case 0 -> PartyIdentifierPageBoundary.Direction.NEXT;
            case 1 -> PartyIdentifierPageBoundary.Direction.PREVIOUS;
            default -> throw invalidCursor();
        };
        long seconds = buffer.getLong();
        int nanos = buffer.getInt();
        UUID id = new UUID(buffer.getLong(), buffer.getLong());
        int limit = buffer.getInt();
        byte[] digest = new byte[DIGEST_BYTES];
        buffer.get(digest);
        if (nanos < 0 || nanos > 999_999_999 || limit != scope.limit()
                || !MessageDigest.isEqual(scopeDigest(scope, key), digest)) {
            throw invalidCursor();
        }
        try {
            return new PartyIdentifierPageBoundary(direction,
                    new PartyIdentifierPagePosition(Instant.ofEpochSecond(seconds, nanos), new PartyIdentifierId(id)));
        } catch (DateTimeException _) {
            throw invalidCursor();
        }
    }

    private static String[] tokenParts(String token) {
        if (token.isBlank() || token.length() > 256) {
            throw invalidCursor();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 4 || !"1".equals(parts[0]) || !parts[1].matches("[A-Za-z0-9_-]{1,64}")
                || parts[2].length() != 87 || parts[3].length() != 43) {
            throw invalidCursor();
        }
        return parts;
    }

    private static byte[] decodeCanonical(String value, int length) {
        try {
            byte[] decoded = DECODER.decode(value);
            if (decoded.length != length || !ENCODER.encodeToString(decoded).equals(value)) {
                throw invalidCursor();
            }
            return decoded;
        } catch (IllegalArgumentException _) {
            throw invalidCursor();
        }
    }

    private static byte[] scopeDigest(IdentifierSearchScope scope, SecretKey key) {
        Mac mac = initializedMac(SCOPE_CONTEXT, key);
        updateUuid(mac, scope.tenantId().value());
        updateUuid(mac, scope.schemeId().value());
        updateText(mac, scope.schemeCode());
        updateText(mac, scope.selector().normalizerKey());
        updateInt(mac, scope.selector().normalizationVersion().value());
        updateText(mac, scope.selector().normalizedValue());
        updateStates(mac, PartyIdentifierStatus.values(), scope.filters().identifierStatuses());
        updateStates(mac, PartyRecordStatus.values(), scope.filters().partyRecordStatuses());
        updateInt(mac, scope.limit());
        return mac.doFinal();
    }

    private static <E extends Enum<E>> void updateStates(Mac mac, E[] universe, Set<E> selected) {
        updateInt(mac, selected.size());
        for (E state : universe) {
            if (selected.contains(state)) updateText(mac, state.name());
        }
    }

    /** Streams exact UTF-16 units with a length prefix; no plaintext byte buffer or replacement encoding is retained. */
    private static void updateText(Mac mac, String value) {
        updateInt(mac, value.length());
        for (int index = 0; index < value.length(); index++) {
            char unit = value.charAt(index);
            mac.update((byte) (unit >>> 8));
            mac.update((byte) unit);
        }
    }

    private static void updateInt(Mac mac, int value) {
        mac.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static void updateUuid(Mac mac, UUID id) {
        mac.update(ByteBuffer.allocate(2 * Long.BYTES).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array());
    }

    private static byte[] authenticate(String signed, SecretKey key) {
        return initializedMac(TOKEN_CONTEXT, key).doFinal(signed.getBytes(StandardCharsets.US_ASCII));
    }

    private static Mac initializedMac(byte[] context, SecretKey key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(context);
            return mac;
        } catch (GeneralSecurityException _) {
            throw new IllegalStateException("Identifier search cursor authentication failed");
        }
    }

    private static ApplicationException invalidCursor() {
        return new ApplicationException(new ApplicationFailure.InvalidIdentifierCursor());
    }
}
