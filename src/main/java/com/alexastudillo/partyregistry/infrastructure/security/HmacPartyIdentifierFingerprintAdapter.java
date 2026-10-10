package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionRequest;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreationRequest;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierFingerprintPort;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Streams bounded exact effective inputs into identifier-only HMAC identities using existing validated registration material. */
@ApplicationScoped
public class HmacPartyIdentifierFingerprintAdapter implements PartyIdentifierFingerprintPort {

    private static final byte[] CONTEXT = "party-identifier-effective-request-fingerprint-v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final HexFormat HEX = HexFormat.of();
    private final SecretKey key;

    /** Reuses eagerly validated external key material without introducing another secret or altering existing fingerprints. */
    @Inject
    public HmacPartyIdentifierFingerprintAdapter(CryptographicKeyMaterial material) {
        this(material.registrationHmacKey());
    }

    HmacPartyIdentifierFingerprintAdapter(SecretKey key) {
        this.key = Objects.requireNonNull(key, "key");
    }

    @Override
    public String fingerprint(TenantId tenant, PartyIdentifierCreationRequest request) {
        Objects.requireNonNull(request, "request");
        return compute(tenant, PartyIdentifierOperation.CREATE, request.partyId(), output -> {
            writeString(output, request.identifierSchemeCode(), 64);
            writeString(output, request.value(), 256);
            String issuer = request.issuerCode();
            if (issuer != null && issuer.codePointCount(0, issuer.length()) > 64) {
                throw invalidBound();
            }
            writeString(output, issuer, 128);
            writeDate(output, request.issuedOn());
            writeDate(output, request.expiresOn());
            output.writeBoolean(request.isPrimary());
        });
    }

    @Override
    public String fingerprint(TenantId tenant, PartyIdentifierActionRequest request) {
        Objects.requireNonNull(request, "request");
        return compute(tenant, request.operation(), request.partyId(), output -> {
            writeUuid(output, request.identifierId().value());
            output.writeLong(request.expectedVersion().value());
            writeString(output, request.reason().reasonCode(), 128);
            writeString(output, request.reason().reasonDetail(), 2000);
            output.writeBoolean(request.replaceCurrentPrimary());
        });
    }

    @Override
    public boolean matches(String storedFingerprint, String candidateFingerprint) {
        byte[] stored = decode(storedFingerprint);
        byte[] candidate = decode(candidateFingerprint);
        boolean valid = stored != null && candidate != null;
        byte[] expected = stored == null ? new byte[32] : stored;
        byte[] actual = candidate == null ? new byte[32] : candidate;
        try {
            return MessageDigest.isEqual(expected, actual) && valid;
        } finally {
            Arrays.fill(expected, (byte) 0);
            Arrays.fill(actual, (byte) 0);
        }
    }

    private String compute(TenantId tenant, PartyIdentifierOperation operation, PartyId party, InputWriter input) {
        Objects.requireNonNull(tenant, "tenant");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(CONTEXT);
            try (var output = new DataOutputStream(new MacSink(mac))) {
                output.writeInt(1);
                writeUuid(output, tenant.value());
                writeString(output, operation.namespace(), 64);
                writeUuid(output, party.value());
                input.write(output);
                output.flush();
            }
            return HEX.formatHex(mac.doFinal());
        } catch (GeneralSecurityException | IOException _) {
            throw new IllegalStateException("Identifier fingerprinting failed");
        }
    }

    private static void writeString(DataOutputStream output, @Nullable String value, int maximumUnits) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        if (value.length() > maximumUnits) {
            throw invalidBound();
        }
        output.writeInt(value.length());
        // Big-endian code units preserve exact controls and lone surrogates without UTF-8 replacement.
        output.writeChars(value);
    }

    private static void writeDate(DataOutputStream output, @Nullable LocalDate value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeLong(value.toEpochDay());
        }
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static byte @Nullable [] decode(@Nullable String value) {
        if (value == null || value.length() != 64) {
            return null;
        }
        try {
            return HEX.parseHex(value);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private static IllegalArgumentException invalidBound() {
        return new IllegalArgumentException("Identifier fingerprint input exceeds its supported bound");
    }

    /** Encodes one action-specific body into the bounded authenticated stream. */
    @FunctionalInterface
    private interface InputWriter {
        void write(DataOutputStream output) throws IOException;
    }

    /** Feeds bytes directly to a per-call MAC without retaining a canonical raw request buffer. */
    private static final class MacSink extends OutputStream {
        private final Mac mac;

        private MacSink(Mac mac) {
            this.mac = mac;
        }

        @Override
        public void write(int value) {
            mac.update((byte) value);
        }

        @Override
        public void write(byte[] value, int offset, int length) {
            mac.update(value, offset, length);
        }
    }
}
