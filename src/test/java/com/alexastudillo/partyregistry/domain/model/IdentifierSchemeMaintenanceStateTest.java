package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies the complete lifecycle-state/property maintenance matrix before candidate validation or mutation. */
class IdentifierSchemeMaintenanceStateTest {

    @ParameterizedTest
    @MethodSource("stateAndProperty")
    void checksEveryEditablePropertyInEveryLifecycleState(IdentifierSchemeStatus state, String property) {
        var current = scheme(state);
        var original = scheme(state);
        var changes = changes(property);
        boolean allowed = state == IdentifierSchemeStatus.DRAFT
                || state != IdentifierSchemeStatus.RETIRED && (property.equals("name") || property.equals("description"));
        if (allowed) {
            assertDoesNotThrow(() -> current.requireMaintenanceAllowed(changes));
        } else {
            var expected = state == IdentifierSchemeStatus.RETIRED
                    ? DomainViolation.IDENTIFIER_SCHEME_RETIRED : DomainViolation.IDENTIFIER_SCHEME_RULES_LOCKED;
            assertEquals(expected, assertThrows(DomainValidationException.class,
                    () -> current.requireMaintenanceAllowed(changes)).violation());
        }
        assertEquals(original, current);
    }

    @Test
    void aLockedPropertyRejectsTheWholeIntentEvenWithAnAllowedName() {
        var current = scheme(IdentifierSchemeStatus.ACTIVE);
        var mixed = new IdentifierSchemeChanges(FieldUpdate.present("New name"), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.present(false));
        assertEquals(DomainViolation.IDENTIFIER_SCHEME_RULES_LOCKED, assertThrows(DomainValidationException.class,
                () -> current.requireMaintenanceAllowed(mixed)).violation());
        assertEquals(scheme(IdentifierSchemeStatus.ACTIVE), current);
    }

    @Test
    void retirementRestrictionPrecedesConfigurationRangeEvaluation() {
        var changes = new IdentifierSchemeChanges(FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.present(new BigInteger("999999999999999999999")),
                FieldUpdate.absent());
        var retired = scheme(IdentifierSchemeStatus.RETIRED);
        assertEquals(DomainViolation.IDENTIFIER_SCHEME_RETIRED, assertThrows(DomainValidationException.class,
                () -> retired.requireMaintenanceAllowed(changes)).violation());
        var deprecated = scheme(IdentifierSchemeStatus.DEPRECATED);
        assertEquals(DomainViolation.IDENTIFIER_SCHEME_RULES_LOCKED, assertThrows(DomainValidationException.class,
                () -> deprecated.requireMaintenanceAllowed(changes)).violation());
    }

    private static IdentifierSchemeChanges changes(String property) {
        return new IdentifierSchemeChanges(property.equals("name") ? FieldUpdate.present("Scheme") : FieldUpdate.absent(),
                property.equals("description") ? FieldUpdate.present(null) : FieldUpdate.absent(),
                property.equals("normalizerKey") ? FieldUpdate.present("TRIM_UPPERCASE_V1") : FieldUpdate.absent(),
                property.equals("validatorKey") ? FieldUpdate.present("ALPHANUMERIC_V1") : FieldUpdate.absent(),
                property.equals("minimumLength") ? FieldUpdate.present(BigInteger.ONE) : FieldUpdate.absent(),
                property.equals("maximumLength") ? FieldUpdate.present(BigInteger.TEN) : FieldUpdate.absent(),
                property.equals("requiresExpiration") ? FieldUpdate.present(false) : FieldUpdate.absent());
    }

    private static IdentifierScheme scheme(IdentifierSchemeStatus status) {
        return new IdentifierScheme(new IdentifierSchemeId(UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd997")),
                "CODE", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, "Scheme", "Description",
                "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", 1, 10, false, status, new IdentifierSchemeVersion(3),
                AuditInfo.initial(Instant.parse("2026-09-30T00:00:00Z"), "catalog"));
    }

    private static Stream<Arguments> stateAndProperty() {
        return Stream.of(IdentifierSchemeStatus.values()).flatMap(status -> Stream.of("name", "description",
                "normalizerKey", "validatorKey", "minimumLength", "maximumLength", "requiresExpiration")
                .map(property -> Arguments.of(status, property)));
    }
}
