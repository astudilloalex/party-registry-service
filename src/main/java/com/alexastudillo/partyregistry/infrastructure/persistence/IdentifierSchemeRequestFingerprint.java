package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemeCreateInput;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeEffectiveRequest;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleRequest;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Hashes versioned, tenant-scoped exact scheme intent without retry keys, actors, or process correlation. */
final class IdentifierSchemeRequestFingerprint {

    private IdentifierSchemeRequestFingerprint() {
    }

    /** Encodes full effective intent; callers retain and compare the request as well as its digest. */
    static String fingerprint(TenantId tenant, IdentifierSchemeEffectiveRequest request) {
        try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) {
            text(out, "identifier-scheme-request-fingerprint-v1");
            text(out, request.operation());
            uuid(out, tenant.value());
            switch (request) {
                case IdentifierSchemeCreateInput(
                        var code, var issuingCountryCode, var category, var applicableSubjectType,
                        var name, var description, var normalizerKey, var validatorKey,
                        var minimumLength, var maximumLength, var requiresExpiration) -> {
                    text(out, code);
                    text(out, issuingCountryCode);
                    text(out, category.name());
                    text(out, applicableSubjectType.name());
                    text(out, name);
                    text(out, description);
                    text(out, normalizerKey);
                    text(out, validatorKey);
                    integer(out, minimumLength);
                    integer(out, maximumLength);
                    out.writeBoolean(requiresExpiration);
                }
                case IdentifierSchemeLifecycleRequest(var action, var schemeId, var expectedVersion) -> {
                    text(out, action.name());
                    uuid(out, schemeId.value());
                    out.writeLong(expectedVersion.value());
                }
            }
            out.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException _) {
            throw new IllegalStateException("Identifier scheme fingerprinting failed");
        }
    }

    static boolean matches(@Nullable String stored, @Nullable String expected) {
        return stored != null && expected != null && stored.matches("[0-9a-f]{64}")
                && expected.matches("[0-9a-f]{64}")
                && MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII), expected.getBytes(StandardCharsets.US_ASCII));
    }

    private static void uuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    private static void text(DataOutputStream out, @Nullable String value) throws IOException {
        if (value == null) {
            out.writeInt(-1);
        } else {
            byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
            out.writeInt(utf8.length);
            out.write(utf8);
        }
    }

    private static void integer(DataOutputStream out, @Nullable BigInteger value) throws IOException {
        text(out, value == null ? null : value.toString());
    }
}
