package com.alexastudillo.partyregistry.application.idempotency;

import com.alexastudillo.partyregistry.application.command.CreateNaturalPersonCommand;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Computes deterministic fingerprints of effective natural-person create
 * commands.
 *
 * <p>
 * The canonical form serializes the effective command fields in a fixed
 * order, represents omitted and null optional values uniformly as null, and
 * excludes idempotency-key, user, and process correlation values so that
 * equivalent requests produce identical hashes.
 */
public final class CreateCommandFingerprint {

    private CreateCommandFingerprint() {
    }

    /**
     * Builds the canonical serialization of an effective create command.
     *
     * @param operation stable operation key
     * @param command   effective create command
     * @return the deterministic canonical form
     */
    public static String canonicalize(String operation, CreateNaturalPersonCommand command) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(command, "command");

        StringBuilder canonical = new StringBuilder(256);
        canonical.append('{');
        appendStringField(canonical, "operation", operation, true);
        appendStringField(canonical, "tenantId", command.tenantId().value().toString(), false);
        appendStringField(canonical, "displayName", command.displayName(), false);
        appendStringField(canonical, "givenNames", command.givenNames(), false);
        appendStringField(canonical, "familyNames", command.familyNames(), false);
        appendStringField(canonical, "preferredName", command.preferredName(), false);
        appendStringField(
                canonical,
                "birthDate",
                Objects.toString(command.birthDate(), null),
                false);
        appendStringField(
                canonical,
                "dateOfDeath",
                Objects.toString(command.dateOfDeath(), null),
                false);
        appendStringField(canonical, "birthCountryCode", command.birthCountryCode(), false);
        canonical.append('}');
        return canonical.toString();
    }

    /**
     * Computes the SHA-256 fingerprint of an effective create command.
     *
     * @param operation stable operation key
     * @param command   effective create command
     * @return lowercase hexadecimal SHA-256 hash of the canonical form
     */
    public static String hashOf(String operation, CreateNaturalPersonCommand command) {
        String canonical = canonicalize(operation, command);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for request fingerprinting", exception);
        }
    }

    private static void appendStringField(
            StringBuilder canonical,
            String name,
            @Nullable String value,
            boolean first) {
        if (!first) {
            canonical.append(',');
        }
        canonical.append('"').append(name).append("\":");
        if (value == null) {
            canonical.append("null");
        } else {
            appendEscaped(canonical, value);
        }
    }

    private static void appendEscaped(StringBuilder canonical, String value) {
        canonical.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> canonical.append("\\\"");
                case '\\' -> canonical.append("\\\\");
                case '\b' -> canonical.append("\\b");
                case '\f' -> canonical.append("\\f");
                case '\n' -> canonical.append("\\n");
                case '\r' -> canonical.append("\\r");
                case '\t' -> canonical.append("\\t");
                default -> {
                    if (character < 0x20 || Character.isSurrogate(character)) {
                        canonical.append(String.format("\\u%04x", (int) character));
                    } else {
                        canonical.append(character);
                    }
                }
            }
        }
        canonical.append('"');
    }
}
