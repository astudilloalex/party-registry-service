package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.command.InitialPartyIdentifierInput;
import com.alexastudillo.partyregistry.application.command.PartyRegistrationCommand;
import com.alexastudillo.partyregistry.application.command.RegisterLegalEntityCommand;
import com.alexastudillo.partyregistry.application.command.RegisterNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.port.RegistrationFingerprintPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Produces registration fingerprints with a dedicated HMAC-SHA-256 key.
 */
@ApplicationScoped
public class HmacRegistrationFingerprintAdapter implements RegistrationFingerprintPort {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final byte[] FINGERPRINT_CONTEXT = "party-registration-fingerprint-v1"
            .getBytes(StandardCharsets.US_ASCII);
    private static final HexFormat LOWERCASE_HEX = HexFormat.of();

    private final SecretKey registrationHmacKey;

    /**
     * Creates the adapter from eagerly validated in-memory key material.
     *
     * @param keyMaterial validated cryptographic keys
     */
    @Inject
    public HmacRegistrationFingerprintAdapter(CryptographicKeyMaterial keyMaterial) {
        this(keyMaterial.registrationHmacKey());
    }

    HmacRegistrationFingerprintAdapter(SecretKey registrationHmacKey) {
        this.registrationHmacKey = Objects.requireNonNull(registrationHmacKey, "registrationHmacKey");
    }

    @Override
    public String fingerprint(PartyRegistrationCommand command) {
        Objects.requireNonNull(command, "command");
        byte[] canonicalInput = canonicalForm(command).getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(registrationHmacKey);
            mac.update(FINGERPRINT_CONTEXT);
            mac.update((byte) 0);
            return LOWERCASE_HEX.formatHex(mac.doFinal(canonicalInput));
        } catch (GeneralSecurityException | IllegalArgumentException _) {
            throw new IllegalStateException("Registration fingerprinting failed");
        } finally {
            Arrays.fill(canonicalInput, (byte) 0);
        }
    }

    @Override
    public boolean matches(String expectedFingerprint, String actualFingerprint) {
        boolean expectedValid = isEncodedFingerprint(expectedFingerprint);
        boolean actualValid = isEncodedFingerprint(actualFingerprint);
        boolean valid = expectedValid && actualValid;
        byte[] expected = decodeOrZero(expectedFingerprint);
        byte[] actual = decodeOrZero(actualFingerprint);
        try {
            return MessageDigest.isEqual(expected, actual) && valid;
        } finally {
            Arrays.fill(expected, (byte) 0);
            Arrays.fill(actual, (byte) 0);
        }
    }

    static String canonicalForm(PartyRegistrationCommand command) {
        Objects.requireNonNull(command, "command");
        StringBuilder canonical = new StringBuilder(512).append('{');
        appendStringField(canonical, "operation", command.operation());
        appendStringField(canonical, "tenantId", command.tenantId().value().toString());
        appendStringField(canonical, "partyType", command.partyType().name());

        switch (command) {
            case RegisterNaturalPersonCommand naturalPerson -> {
                appendStringField(canonical, "displayName", naturalPerson.displayName());
                appendStringField(canonical, "givenNames", naturalPerson.givenNames());
                appendStringField(canonical, "familyNames", naturalPerson.familyNames());
                appendStringField(canonical, "preferredName", naturalPerson.preferredName());
                appendStringField(canonical, "birthDate", Objects.toString(naturalPerson.birthDate(), null));
                appendStringField(canonical, "dateOfDeath", Objects.toString(naturalPerson.dateOfDeath(), null));
                appendStringField(canonical, "birthCountryCode", naturalPerson.birthCountryCode());
            }
            case RegisterLegalEntityCommand legalEntity -> {
                appendStringField(canonical, "displayName", legalEntity.displayName());
                appendStringField(canonical, "legalName", legalEntity.legalName());
                appendStringField(canonical, "tradeName", legalEntity.tradeName());
                appendStringField(canonical, "legalFormCode", legalEntity.legalFormCode());
                appendStringField(canonical, "incorporationCountryCode", legalEntity.incorporationCountryCode());
                appendStringField(canonical, "incorporatedOn", Objects.toString(legalEntity.incorporatedOn(), null));
                appendStringField(canonical, "dissolvedOn", Objects.toString(legalEntity.dissolvedOn(), null));
            }
        }

        InitialPartyIdentifierInput identifier = command.initialIdentifier();
        appendStringField(canonical, "identifierSchemeCode", identifier.identifierSchemeCode());
        appendStringField(canonical, "identifierValue", identifier.value());
        appendStringField(canonical, "identifierIssuerCode", identifier.issuerCode());
        appendStringField(canonical, "identifierIssuedOn", Objects.toString(identifier.issuedOn(), null));
        appendStringField(canonical, "identifierExpiresOn", Objects.toString(identifier.expiresOn(), null));
        appendBooleanField(canonical, "identifierIsPrimary", identifier.isPrimary());
        return canonical.append('}').toString();
    }

    private static void appendStringField(
            StringBuilder canonical,
            String name,
            @Nullable String value) {
        appendSeparator(canonical);
        canonical.append('"').append(name).append("\":");
        if (value == null) {
            canonical.append("null");
            return;
        }
        appendEscaped(canonical, value);
    }

    private static void appendBooleanField(StringBuilder canonical, String name, boolean value) {
        appendSeparator(canonical);
        canonical.append('"').append(name).append("\":").append(value);
    }

    private static void appendSeparator(StringBuilder canonical) {
        if (canonical.length() > 1) {
            canonical.append(',');
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
                        appendUnicodeEscape(canonical, character);
                    } else {
                        canonical.append(character);
                    }
                }
            }
        }
        canonical.append('"');
    }

    private static void appendUnicodeEscape(StringBuilder canonical, char character) {
        canonical.append("\\u");
        String hexadecimal = Integer.toHexString(character);
        canonical.append("0".repeat(4 - hexadecimal.length())).append(hexadecimal);
    }

    private static boolean isEncodedFingerprint(@Nullable String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        int invalid = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            invalid |= ((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f')
                    || (character >= 'A' && character <= 'F')) ? 0 : 1;
        }
        return invalid == 0;
    }

    private static byte[] decodeOrZero(@Nullable String value) {
        if (!isEncodedFingerprint(value)) {
            return new byte[32];
        }
        return LOWERCASE_HEX.parseHex(value);
    }
}
