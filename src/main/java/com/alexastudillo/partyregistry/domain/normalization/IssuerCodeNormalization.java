package com.alexastudillo.partyregistry.domain.normalization;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Defines canonical optional issuing-entity metadata on new writes without changing stored history. */
public final class IssuerCodeNormalization {

    public static final int MAX_CODE_POINTS = 64;

    private IssuerCodeNormalization() {
    }

    /**
     * Returns locale-independent uppercase or null for absent/blank input, without trimming nonblank codes.
     *
     * @param value original submitted issuer text
     * @return canonical optional issuing-entity code
     * @throws DomainValidationException when the original input exceeds its bound or has invalid structure
     */
    public static @Nullable String forWrite(@Nullable String value) {
        validateSubmitted(value);
        return value == null || value.isBlank() ? null : value.toUpperCase(Locale.ROOT);
    }

    /** Validates new/supplied text before canonicalization; historical restoration does not call this method. */
    public static void validateSubmitted(@Nullable String value) {
        if (value != null && value.codePointCount(0, value.length()) > MAX_CODE_POINTS) {
            throw new DomainValidationException(DomainViolation.IDENTIFIER_ISSUER_CODE_TOO_LONG,
                    "Submitted issuer code exceeds 64 Unicode code points");
        }
        if (!hasValidStructure(value)) {
            throw new DomainValidationException(DomainViolation.IDENTIFIER_ISSUER_CODE_INVALID,
                    "Issuer code requires ASCII letter groups separated by single underscores");
        }
    }

    /** Accepts null/Java-blank text or ASCII letter groups without performing a lookup or changing the input. */
    public static boolean hasValidStructure(@Nullable String value) {
        if (value == null || value.isBlank()) return true;
        boolean previousLetter = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (isAsciiLetter(character)) {
                previousLetter = true;
            } else if (character == '_' && previousLetter) {
                previousLetter = false;
            } else {
                return false;
            }
        }
        return previousLetter;
    }

    private static boolean isAsciiLetter(char value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z';
    }
}
