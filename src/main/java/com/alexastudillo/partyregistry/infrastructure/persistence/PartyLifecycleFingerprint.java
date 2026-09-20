package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyLifecycleRequest;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Fingerprints a versioned, unambiguous lifecycle identity without registration secrets or retry attribution.
 */
final class PartyLifecycleFingerprint {

    private static final byte[] CONTEXT = "party-lifecycle-request-v1\0".getBytes(StandardCharsets.US_ASCII);

    private PartyLifecycleFingerprint() {
    }

    static String fingerprint(TenantId tenant, PartyLifecycleRequest request) {
        UUID owner = tenant.value();
        UUID party = request.partyId().value();
        byte action = switch (request.action()) {
            case ACTIVATE -> 1;
            case DEACTIVATE -> 2;
            case ARCHIVE -> 3;
        };
        byte[] identity = ByteBuffer.allocate(41)
                .putLong(owner.getMostSignificantBits()).putLong(owner.getLeastSignificantBits())
                .put(action).putLong(party.getMostSignificantBits()).putLong(party.getLeastSignificantBits())
                .putLong(request.expectedVersion().value()).array();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(CONTEXT);
            return HexFormat.of().formatHex(digest.digest(identity));
        } catch (NoSuchAlgorithmException _) {
            throw new IllegalStateException("Lifecycle fingerprinting is unavailable");
        }
    }

    static boolean matches(String stored, String calculated) {
        return stored != null && stored.matches("[0-9a-f]{64}")
                && MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII), calculated.getBytes(StandardCharsets.US_ASCII));
    }
}
