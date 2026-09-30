package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.CreateNationalityCommand;
import com.alexastudillo.partyregistry.application.command.SetPrimaryNationalityCommand;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/** Hashes unambiguous effective nationality inputs without keys, actor, or process attribution. */
final class NationalityRequestFingerprint {

    private NationalityRequestFingerprint() {
    }

    static String create(CreateNationalityCommand command) {
        try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) {
            begin(out, NationalityOperation.CREATE, command.requestMetadata().tenantId());
            uuid(out, command.partyId().value());
            text(out, command.countryCode());
            out.writeBoolean(command.isPrimary());
            date(out, command.period().validFrom());
            date(out, command.period().validUntil());
            return hash(bytes, out);
        } catch (IOException _) {
            throw new IllegalStateException("Nationality creation fingerprinting failed");
        }
    }

    static String setPrimary(SetPrimaryNationalityCommand command) {
        try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) {
            begin(out, NationalityOperation.SET_PRIMARY, command.requestMetadata().tenantId());
            uuid(out, command.partyId().value());
            uuid(out, command.nationalityId().value());
            // The captured date controls eligibility on a new attempt, not the identity of a completed replay.
            return hash(bytes, out);
        } catch (IOException _) {
            throw new IllegalStateException("Nationality primary fingerprinting failed");
        }
    }

    static boolean matches(String stored, String expected) {
        return stored != null && stored.matches("[0-9a-f]{64}")
                && MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII),
                        expected.getBytes(StandardCharsets.US_ASCII));
    }

    private static void begin(DataOutputStream out, NationalityOperation operation, TenantId tenant) throws IOException {
        text(out, "nationality-request-fingerprint-v1");
        text(out, operation.label());
        uuid(out, tenant.value());
    }

    private static void uuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(utf8.length);
        out.write(utf8);
    }

    private static void date(DataOutputStream out, LocalDate value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeLong(value.toEpochDay());
        }
    }

    private static String hash(ByteArrayOutputStream bytes, DataOutputStream out) throws IOException {
        out.flush();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (NoSuchAlgorithmException _) {
            throw new IllegalStateException("Nationality fingerprint algorithm is unavailable");
        }
    }
}
