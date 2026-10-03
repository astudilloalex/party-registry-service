package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Protects exact integral admission and presence-aware length-range maintenance without infrastructure. */
class IdentifierSchemeLengthBoundsTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 32767})
    void acceptsEqualInclusiveBoundaryValues(int value) {
        var bounds = IdentifierSchemeLengthBounds.fromIntegralInputs(BigInteger.valueOf(value), BigInteger.valueOf(value));
        assertEquals(new IdentifierSchemeLengthBounds(value, value), bounds);
    }

    @Test
    void acceptsAbsentOrSingleOptionalBounds() {
        var absent = IdentifierSchemeLengthBounds.fromIntegralInputs(null, null);
        assertNull(absent.minimumLength());
        assertNull(absent.maximumLength());
        assertEquals(new IdentifierSchemeLengthBounds(1, null),
                IdentifierSchemeLengthBounds.fromIntegralInputs(BigInteger.ONE, null));
        assertEquals(new IdentifierSchemeLengthBounds(null, 32767),
                IdentifierSchemeLengthBounds.fromIntegralInputs(null, BigInteger.valueOf(32767)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "32768", "2147483648", "999999999999999999999999999999999999"})
    void rejectsInvalidIntegralBoundsBeforeNarrowing(String value) {
        var exact = new BigInteger(value);
        assertEquals(DomainViolation.IDENTIFIER_MINIMUM_LENGTH_INVALID,
                assertThrows(DomainValidationException.class,
                        () -> IdentifierSchemeLengthBounds.fromIntegralInputs(exact, null)).violation());
        assertEquals(DomainViolation.IDENTIFIER_MAXIMUM_LENGTH_INVALID,
                assertThrows(DomainValidationException.class,
                        () -> IdentifierSchemeLengthBounds.fromIntegralInputs(null, exact)).violation());
    }

    @Test
    void rejectsIncoherentBoundsAndDirectInvalidConstruction() {
        var five = BigInteger.valueOf(5);
        assertEquals(DomainViolation.IDENTIFIER_LENGTH_RANGE_INVALID, assertThrows(DomainValidationException.class,
                () -> IdentifierSchemeLengthBounds.fromIntegralInputs(BigInteger.TEN, five)).violation());
        assertEquals(DomainViolation.IDENTIFIER_MINIMUM_LENGTH_INVALID, assertThrows(DomainValidationException.class,
                () -> new IdentifierSchemeLengthBounds(0, null)).violation());
        assertEquals(DomainViolation.IDENTIFIER_MAXIMUM_LENGTH_INVALID, assertThrows(DomainValidationException.class,
                () -> new IdentifierSchemeLengthBounds(null, 32768)).violation());
    }

    @Test
    void mergesOnlySuppliedBoundsAndAllowsExplicitClearing() {
        var original = new IdentifierSchemeLengthBounds(10, 20);
        assertEquals(new IdentifierSchemeLengthBounds(10, 30), original.merge(
                FieldUpdate.absent(), FieldUpdate.present(BigInteger.valueOf(30))));
        assertEquals(new IdentifierSchemeLengthBounds(null, 20), original.merge(
                FieldUpdate.present(null), FieldUpdate.absent()));
        assertEquals(new IdentifierSchemeLengthBounds(10, null), original.merge(
                FieldUpdate.absent(), FieldUpdate.present(null)));
        assertEquals(original, original.merge(FieldUpdate.absent(), FieldUpdate.absent()));
    }

    @Test
    void validatesAChangedMaximumAgainstTheRetainedMinimumWithoutMutation() {
        var original = new IdentifierSchemeLengthBounds(10, 20);
        var absent = FieldUpdate.<BigInteger>absent();
        var update = FieldUpdate.present(BigInteger.valueOf(5));
        assertEquals(DomainViolation.IDENTIFIER_LENGTH_RANGE_INVALID, assertThrows(DomainValidationException.class,
                () -> original.merge(absent, update)).violation());
        assertEquals(new IdentifierSchemeLengthBounds(10, 20), original);
    }
}
