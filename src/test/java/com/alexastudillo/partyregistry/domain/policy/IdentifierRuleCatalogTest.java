package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies every explicitly supported pure identifier normalizer and validator rule.
 */
class IdentifierRuleCatalogTest {

    private final IdentifierRuleCatalog catalog = new IdentifierRuleCatalog();

    @Test
    void exposesOnlyExplicitVersionedRuleKeys() {
        assertEquals(Set.of("TRIM_UPPERCASE_V1"), catalog.supportedNormalizerKeys());
        assertEquals(Set.of("ALPHANUMERIC_V1", "EC_NATIONAL_ID_V1", "EC_TAX_ID_V1"), catalog.supportedValidatorKeys());
        assertEquals(new IdentifierRuleVersion(1), StandardIdentifierNormalizer.TRIM_UPPERCASE_V1.version());
        assertEquals(new IdentifierRuleVersion(1), StandardIdentifierValidator.ALPHANUMERIC_V1.version());
        assertEquals(new IdentifierRuleVersion(1), StandardIdentifierValidator.EC_NATIONAL_ID_V1.version());
        assertEquals(new IdentifierRuleVersion(1), StandardIdentifierValidator.EC_TAX_ID_V1.version());
    }

    @Test
    void trimUppercaseNormalizerIsDeterministicAtValidBoundaries() {
        IdentifierNormalizer normalizer = StandardIdentifierNormalizer.TRIM_UPPERCASE_V1;

        assertEquals("A", normalizer.normalize(" a "));
        assertEquals("ABC123", normalizer.normalize("  abc123  "));
        assertEquals(normalizer.normalize(" value9 "), normalizer.normalize(" value9 "));
        assertViolation(DomainViolation.IDENTIFIER_VALUE_REQUIRED, () -> normalizer.normalize(null));
        assertViolation(DomainViolation.IDENTIFIER_VALUE_REQUIRED, () -> normalizer.normalize("   "));
    }

    @Test
    void alphanumericValidatorAcceptsAndRejectsItsBoundaries() {
        IdentifierValidator validator = StandardIdentifierValidator.ALPHANUMERIC_V1;

        assertTrue(validator.isValid("A"));
        assertTrue(validator.isValid("0"));
        assertTrue(validator.isValid("ABC123"));
        assertFalse(validator.isValid(""));
        assertFalse(validator.isValid(null));
        assertFalse(validator.isValid("ABC-123"));
        assertFalse(validator.isValid("ABC 123"));
        assertFalse(validator.isValid("abc123"));
    }

    @Test
    void ecuadorNationalIdValidatorAcceptsTerritorialBoundariesAndModuloTenCheckDigits() {
        IdentifierValidator validator = StandardIdentifierValidator.EC_NATIONAL_ID_V1;

        // Synthetic numbers exercise leading zeros, prefix 30, product reduction, and check digit zero.
        for (String value : new String[] {"0100000009", "2400000002", "3000000004", "1710034065", "0190000000"}) {
            assertTrue(validator.isValid(value), value);
        }
    }

    @Test
    void ecuadorNationalIdValidatorRejectsInvalidFormatPrefixAndChecksum() {
        IdentifierValidator validator = StandardIdentifierValidator.EC_NATIONAL_ID_V1;

        for (String value : new String[] {
                null, "", "171003406", "17100340650", " 1710034065 ",
                "171003406A", "A710034065", "+710034065", "171003 065", "171003-065", "171003406\n",
                "\uFF11\uFF17\uFF11\uFF10\uFF10\uFF13\uFF14\uFF10\uFF16\uFF15",
                "\u0661\u0667\u0661\u0660\u0660\u0663\u0664\u0660\u0666\u0665",
                "0000000000", "2500000001", "2900000007", "3100000003", "1710034064"}) {
            assertFalse(validator.isValid(value), String.valueOf(value));
        }
    }

    @Test
    void catalogNormalizesEcuadorNationalIdsBeforeApplyingTheSelectedValidator() {
        IdentifierScheme scheme = scheme("TRIM_UPPERCASE_V1", "EC_NATIONAL_ID_V1");

        assertEquals(new IdentifierRuleResult(
                "0100000009", new IdentifierRuleVersion(1), new IdentifierRuleVersion(1)),
                catalog.evaluate(scheme, " \t0100000009\n "));
        assertViolation(DomainViolation.IDENTIFIER_VALUE_REQUIRED, () -> catalog.evaluate(scheme, null));
        assertViolation(DomainViolation.IDENTIFIER_VALUE_REQUIRED, () -> catalog.evaluate(scheme, "  "));
        assertViolation(DomainViolation.IDENTIFIER_VALUE_INVALID, () -> catalog.evaluate(scheme, "1710034064"));
        assertViolation(DomainViolation.IDENTIFIER_VALUE_INVALID, () -> catalog.evaluate(scheme, "171003 065"));
    }

    @Test
    void ecuadorTaxIdValidatorAcceptsStructureWithoutInferringTypeOrRequiringAChecksum() {
        IdentifierValidator validator = StandardIdentifierValidator.EC_TAX_ID_V1;

        // These synthetic fixtures are structurally acceptable, not verified taxpayer registrations.
        for (String value : new String[] {"0100000009001", "1790000000001", "1760000000001", "1780000000001"}) {
            assertTrue(validator.isValid(value), value);
        }
    }

    @Test
    void ecuadorTaxIdValidatorRejectsInvalidLengthCharactersAndSuffix() {
        IdentifierValidator validator = StandardIdentifierValidator.EC_TAX_ID_V1;

        for (String value : new String[] {
                null, "", "179000000001", "17900000000001", " 1790000000001 ",
                "1790000000000", "1790000000002", "1790000000999", "179000000A001",
                "+790000000001", "179000000 001", "179000000-001", "179000000\n001", "1790000000001\n",
                "\uFF11790000000001", "\u0661790000000001"}) {
            assertFalse(validator.isValid(value), String.valueOf(value));
        }
    }

    @Test
    void catalogNormalizesEcuadorTaxIdsBeforeApplyingStructuralValidation() {
        IdentifierScheme scheme = scheme("TRIM_UPPERCASE_V1", "EC_TAX_ID_V1");

        assertEquals(new IdentifierRuleResult(
                "0100000009001", new IdentifierRuleVersion(1), new IdentifierRuleVersion(1)),
                catalog.evaluate(scheme, " \t0100000009001\n "));
        assertViolation(DomainViolation.IDENTIFIER_VALUE_REQUIRED, () -> catalog.evaluate(scheme, null));
        assertViolation(DomainViolation.IDENTIFIER_VALUE_REQUIRED, () -> catalog.evaluate(scheme, "  "));
        assertViolation(DomainViolation.IDENTIFIER_VALUE_INVALID, () -> catalog.evaluate(scheme, "1790000000002"));
        assertViolation(DomainViolation.IDENTIFIER_VALUE_INVALID, () -> catalog.evaluate(scheme, "179000000A001"));
    }

    @Test
    void catalogReturnsNormalizedValueAndExactRuleVersions() {
        IdentifierScheme scheme = scheme("TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1");

        IdentifierRuleResult first = catalog.evaluate(scheme, "  abc123  ");
        IdentifierRuleResult second = catalog.evaluate(scheme, "  abc123  ");

        assertEquals(new IdentifierRuleResult(
                "ABC123",
                new IdentifierRuleVersion(1),
                new IdentifierRuleVersion(1)), first);
        assertEquals(first, second);
    }

    @Test
    void catalogRejectsSemanticallyInvalidValues() {
        IdentifierScheme scheme = scheme("TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1");

        assertViolation(DomainViolation.IDENTIFIER_VALUE_INVALID,
                () -> catalog.evaluate(scheme, "ABC-123"));
    }

    @Test
    void unsupportedActiveCatalogKeysAreInternalInvariantFailures() {
        IdentifierScheme unsupportedNormalizer = scheme("UNKNOWN_NORMALIZER_V1", "ALPHANUMERIC_V1");
        IdentifierScheme unsupportedValidator = scheme("TRIM_UPPERCASE_V1", "UNKNOWN_VALIDATOR_V1");

        assertViolation(DomainViolation.IDENTIFIER_RULE_CATALOG_INVALID,
                () -> catalog.evaluate(unsupportedNormalizer, "ABC123"));
        assertViolation(DomainViolation.IDENTIFIER_RULE_CATALOG_INVALID,
                () -> catalog.evaluate(unsupportedValidator, "ABC123"));
    }

    private static IdentifierScheme scheme(String normalizerKey, String validatorKey) {
        return new IdentifierScheme(
                new IdentifierSchemeId(UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd605")),
                "GENERIC-ID",
                "EC",
                IdentifierCategory.OTHER,
                IdentifierSubjectType.BOTH,
                "Generic official identifier",
                null,
                normalizerKey,
                validatorKey,
                1,
                64,
                false,
                IdentifierSchemeStatus.ACTIVE,
                IdentifierSchemeVersion.initial(),
                AuditInfo.initial(Instant.parse("2026-08-30T10:00:00Z"), "catalog-admin"));
    }

    private static void assertViolation(DomainViolation violation, Runnable action) {
        DomainValidationException failure = assertThrows(DomainValidationException.class, action::run);
        assertEquals(violation, failure.violation());
    }
}
