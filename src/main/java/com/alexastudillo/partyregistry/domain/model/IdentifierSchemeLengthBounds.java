package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Objects;

/** Enforces optional inclusive length bounds for new scheme configuration without narrowing invalid input. */
public record IdentifierSchemeLengthBounds(@Nullable Integer minimumLength, @Nullable Integer maximumLength) {

    private static final int MAXIMUM_BOUND = 32767;
    private static final BigInteger MAXIMUM_INTEGRAL_BOUND = BigInteger.valueOf(MAXIMUM_BOUND);

    public IdentifierSchemeLengthBounds {
        requireBound(minimumLength, DomainViolation.IDENTIFIER_MINIMUM_LENGTH_INVALID);
        requireBound(maximumLength, DomainViolation.IDENTIFIER_MAXIMUM_LENGTH_INVALID);
        if (minimumLength != null && maximumLength != null && maximumLength < minimumLength) {
            throw new DomainValidationException(DomainViolation.IDENTIFIER_LENGTH_RANGE_INVALID,
                    "Identifier maximum length cannot be less than its minimum length");
        }
    }

    /** Validates exact arbitrary-size integer input before producing the bounded Domain representation. */
    public static IdentifierSchemeLengthBounds fromIntegralInputs(
            @Nullable BigInteger minimumLength, @Nullable BigInteger maximumLength) {
        return new IdentifierSchemeLengthBounds(
                convert(minimumLength, DomainViolation.IDENTIFIER_MINIMUM_LENGTH_INVALID),
                convert(maximumLength, DomainViolation.IDENTIFIER_MAXIMUM_LENGTH_INVALID));
    }

    /** Preserves omitted bounds, clears explicitly null bounds, and validates the entire resulting interval. */
    public IdentifierSchemeLengthBounds merge(FieldUpdate<BigInteger> minimum, FieldUpdate<BigInteger> maximum) {
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        BigInteger resultingMinimum = minimum.isPresent() ? minimum.value() : integral(minimumLength);
        BigInteger resultingMaximum = maximum.isPresent() ? maximum.value() : integral(maximumLength);
        return fromIntegralInputs(resultingMinimum, resultingMaximum);
    }

    private static void requireBound(@Nullable Integer value, DomainViolation violation) {
        if (value != null && (value < 1 || value > MAXIMUM_BOUND)) {
            throw new DomainValidationException(violation, "Identifier length bound must be between 1 and 32767");
        }
    }

    private static @Nullable Integer convert(@Nullable BigInteger value, DomainViolation violation) {
        if (value == null) {
            return null;
        }
        if (value.signum() <= 0 || value.compareTo(MAXIMUM_INTEGRAL_BOUND) > 0) {
            throw new DomainValidationException(violation, "Identifier length bound must be between 1 and 32767");
        }
        return value.intValueExact();
    }

    private static @Nullable BigInteger integral(@Nullable Integer value) {
        return value == null ? null : BigInteger.valueOf(value);
    }
}
