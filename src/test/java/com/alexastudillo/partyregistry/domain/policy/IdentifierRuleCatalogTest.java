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
        assertEquals(Set.of("ALPHANUMERIC_V1"), catalog.supportedValidatorKeys());
        assertEquals(new IdentifierRuleVersion(1), StandardIdentifierNormalizer.TRIM_UPPERCASE_V1.version());
        assertEquals(new IdentifierRuleVersion(1), StandardIdentifierValidator.ALPHANUMERIC_V1.version());
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
