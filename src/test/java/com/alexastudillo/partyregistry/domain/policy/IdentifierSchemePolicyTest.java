package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies identifier-scheme identity, lifecycle eligibility, compatibility,
 * and value limits.
 */
class IdentifierSchemePolicyTest {

    private static final LocalDate EVALUATED_ON = LocalDate.of(2026, Month.AUGUST, 30);
    private static final IdentifierSchemePolicy POLICY = new IdentifierSchemePolicy();

    @Test
    void createsSchemeWithInitialLifecycleState() {
        Instant occurredAt = Instant.parse("2026-08-30T10:00:00Z");
        IdentifierScheme scheme = IdentifierScheme.create(
                new IdentifierSchemeId(UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd605")),
                "GENERIC-ID",
                "EC",
                IdentifierCategory.OTHER,
                IdentifierSubjectType.BOTH,
                "Generic official identifier",
                null,
                StandardIdentifierNormalizer.TRIM_UPPERCASE_V1.key(),
                StandardIdentifierValidator.ALPHANUMERIC_V1.key(),
                null,
                null,
                false,
                occurredAt,
                "catalog-admin");

        assertEquals(IdentifierSchemeStatus.DRAFT, scheme.status());
        assertEquals(IdentifierSchemeVersion.initial(), scheme.version());
        assertEquals(AuditInfo.initial(occurredAt, "catalog-admin"), scheme.auditInfo());
    }

    @Test
    void preservesStableSchemeIdentityAndRuleVersion() {
        IdentifierScheme scheme = scheme(
                IdentifierSchemeStatus.ACTIVE,
                IdentifierSubjectType.NATURAL_PERSON,
                2,
                12,
                false);

        assertEquals(UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd605"), scheme.id().value());
        assertEquals("GENERIC-ID", scheme.code());
        assertEquals("EC", scheme.issuingCountryCode());
        assertEquals(IdentifierCategory.NATIONAL_ID, scheme.category());
        assertEquals("TRIM_UPPERCASE_V1", scheme.normalizerKey());
        assertEquals("ALPHANUMERIC_V1", scheme.validatorKey());
        assertEquals(7, scheme.version().value());
    }

    @Test
    void acceptsOnlyActiveCompatibleSchemesForRegistration() {
        IdentifierScheme both = scheme(
                IdentifierSchemeStatus.ACTIVE,
                IdentifierSubjectType.BOTH,
                null,
                null,
                false);
        IdentifierScheme natural = scheme(
                IdentifierSchemeStatus.ACTIVE,
                IdentifierSubjectType.NATURAL_PERSON,
                null,
                null,
                false);
        IdentifierScheme legal = scheme(
                IdentifierSchemeStatus.ACTIVE,
                IdentifierSubjectType.LEGAL_ENTITY,
                null,
                null,
                false);

        POLICY.requireRegistrationEligibility(both, PartyType.NATURAL_PERSON);
        POLICY.requireRegistrationEligibility(both, PartyType.LEGAL_ENTITY);
        POLICY.requireRegistrationEligibility(natural, PartyType.NATURAL_PERSON);
        POLICY.requireRegistrationEligibility(legal, PartyType.LEGAL_ENTITY);
        assertTrue(both.supports(PartyType.NATURAL_PERSON));
        assertFalse(natural.supports(PartyType.LEGAL_ENTITY));
    }

    @Test
    void rejectsUnknownInactiveAndIncompatibleSchemes() {
        assertViolation(DomainViolation.IDENTIFIER_SCHEME_UNKNOWN,
                () -> POLICY.requireRegistrationEligibility(null, PartyType.NATURAL_PERSON));
        for (IdentifierSchemeStatus status : new IdentifierSchemeStatus[] {
                IdentifierSchemeStatus.DRAFT,
                IdentifierSchemeStatus.DEPRECATED,
                IdentifierSchemeStatus.RETIRED }) {
            IdentifierScheme inactive = scheme(status, IdentifierSubjectType.BOTH, null, null, false);
            assertViolation(DomainViolation.IDENTIFIER_SCHEME_INACTIVE,
                    () -> POLICY.requireRegistrationEligibility(inactive,
                            PartyType.NATURAL_PERSON));
        }
        IdentifierScheme incompatible = scheme(
                IdentifierSchemeStatus.ACTIVE,
                IdentifierSubjectType.LEGAL_ENTITY,
                null,
                null,
                false);
        assertViolation(DomainViolation.IDENTIFIER_SCHEME_INCOMPATIBLE,
                () -> POLICY.requireRegistrationEligibility(incompatible, PartyType.NATURAL_PERSON));
    }

    @Test
    void appliesNormalizedLengthLimitsInclusively() {
        IdentifierScheme scheme = scheme(
                IdentifierSchemeStatus.ACTIVE,
                IdentifierSubjectType.BOTH,
                4,
                6,
                false);

        POLICY.validateNormalizedLength(scheme, "A123");
        POLICY.validateNormalizedLength(scheme, "A12345");
        assertViolation(DomainViolation.IDENTIFIER_VALUE_TOO_SHORT,
                () -> POLICY.validateNormalizedLength(scheme, "A12"));
        assertViolation(DomainViolation.IDENTIFIER_VALUE_TOO_LONG,
                () -> POLICY.validateNormalizedLength(scheme, "A123456"));
        assertViolation(DomainViolation.IDENTIFIER_VALUE_REQUIRED,
                () -> POLICY.validateNormalizedLength(scheme, "  "));
    }

    @Test
    void composesExistingRulesForPermissivePassportAdmission() {
        IdentifierScheme passport = scheme(
                IdentifierSchemeStatus.ACTIVE, IdentifierSubjectType.NATURAL_PERSON, 1, 256, true);
        IdentifierRuleCatalog catalog = new IdentifierRuleCatalog();

        POLICY.requireRegistrationEligibility(passport, PartyType.NATURAL_PERSON);
        for (String value : new String[] { "a", "0001234567", "  ab0123456  ", "x".repeat(256) }) {
            IdentifierRuleResult result = catalog.evaluate(passport, value);
            POLICY.validateNormalizedLength(passport, result.normalizedValue());
        }
        assertEquals("AB0123456", catalog.evaluate(passport, "  ab0123456  ").normalizedValue());
        assertEquals("SS", catalog.evaluate(passport, "\u00df").normalizedValue());
        for (String value : new String[] { "AB-123", "AB 123", "P<ECU123", "AB/123", "\uFF111234" }) {
            assertViolation(DomainViolation.IDENTIFIER_VALUE_INVALID,
                    () -> catalog.evaluate(passport, value));
        }
        assertViolation(DomainViolation.IDENTIFIER_VALUE_REQUIRED, () -> catalog.evaluate(passport, "  "));
        for (String value : new String[] { "A".repeat(257), "\u00df".repeat(129) }) {
            String normalizedValue = catalog.evaluate(passport, value).normalizedValue();
            assertViolation(DomainViolation.IDENTIFIER_VALUE_TOO_LONG,
                    () -> POLICY.validateNormalizedLength(passport, normalizedValue));
        }
    }

    @Test
    void enforcesExpirationRequirementAndEvaluationDate() {
        IdentifierScheme required = scheme(
                IdentifierSchemeStatus.ACTIVE,
                IdentifierSubjectType.BOTH,
                null,
                null,
                true);

        POLICY.validateExpiration(required, EVALUATED_ON, EVALUATED_ON);
        POLICY.validateExpiration(required, EVALUATED_ON.plusDays(1), EVALUATED_ON);
        assertViolation(DomainViolation.IDENTIFIER_EXPIRATION_REQUIRED,
                () -> POLICY.validateExpiration(required, null, EVALUATED_ON));
        assertViolation(DomainViolation.IDENTIFIER_EXPIRED,
                () -> POLICY.validateExpiration(required, EVALUATED_ON.minusDays(1), EVALUATED_ON));
        assertViolation(DomainViolation.EVALUATION_DATE_REQUIRED,
                () -> POLICY.validateExpiration(required, EVALUATED_ON, null));

        IdentifierScheme optional = scheme(
                IdentifierSchemeStatus.ACTIVE,
                IdentifierSubjectType.BOTH,
                null,
                null,
                false);
        POLICY.validateExpiration(optional, null, EVALUATED_ON);
    }

    @Test
    void rejectsInvalidSchemeLengthConfigurationAndVersion() {
        assertViolation(DomainViolation.IDENTIFIER_MINIMUM_LENGTH_INVALID,
                () -> scheme(IdentifierSchemeStatus.ACTIVE, IdentifierSubjectType.BOTH, 0, 10, false));
        assertViolation(DomainViolation.IDENTIFIER_MAXIMUM_LENGTH_INVALID,
                () -> scheme(IdentifierSchemeStatus.ACTIVE, IdentifierSubjectType.BOTH, 1, 0, false));
        assertViolation(DomainViolation.IDENTIFIER_LENGTH_RANGE_INVALID,
                () -> scheme(IdentifierSchemeStatus.ACTIVE, IdentifierSubjectType.BOTH, 10, 9, false));
        assertViolation(DomainViolation.IDENTIFIER_SCHEME_VERSION_NEGATIVE,
                () -> new IdentifierSchemeVersion(-1));
    }

    private static IdentifierScheme scheme(
            IdentifierSchemeStatus status,
            IdentifierSubjectType subjectType,
            Integer minimumLength,
            Integer maximumLength,
            boolean requiresExpiration) {
        Instant occurredAt = Instant.parse("2026-08-30T10:00:00Z");
        return new IdentifierScheme(
                new IdentifierSchemeId(UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd605")),
                "GENERIC-ID",
                "EC",
                IdentifierCategory.NATIONAL_ID,
                subjectType,
                "Generic official identifier",
                null,
                StandardIdentifierNormalizer.TRIM_UPPERCASE_V1.key(),
                StandardIdentifierValidator.ALPHANUMERIC_V1.key(),
                minimumLength,
                maximumLength,
                requiresExpiration,
                status,
                new IdentifierSchemeVersion(7),
                AuditInfo.initial(occurredAt, "catalog-admin"));
    }

    private static void assertViolation(DomainViolation violation, Runnable action) {
        DomainValidationException failure = assertThrows(DomainValidationException.class, action::run);
        assertEquals(violation, failure.violation());
    }
}
