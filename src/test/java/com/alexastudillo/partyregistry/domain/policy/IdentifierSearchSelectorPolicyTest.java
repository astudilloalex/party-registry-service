package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies historical search semantics remain pure and independent of registration admission and diagnostic output. */
class IdentifierSearchSelectorPolicyTest {
    private final IdentifierSearchSelectorPolicy policy = new IdentifierSearchSelectorPolicy(new IdentifierRuleCatalog());

    @Test
    void ignoresValidatorLifecycleSubjectAndRegistrationLengthBounds() {
        for (IdentifierSchemeStatus status : IdentifierSchemeStatus.values()) {
            var selector = policy.select(scheme("TRIM_UPPERCASE_V1", "UNKNOWN_VALIDATOR", status), " 17 ");
            assertTrue(selector.matchesPrefix("1710034065"));
            assertFalse(selector.matchesExact("1710034065"));
            assertTrue(selector.matchesExact(" 17 "));
            assertEquals(new IdentifierRuleVersion(1), selector.normalizationVersion());
        }
    }

    @Test
    void retainsLiteralPunctuationAccentsInteriorWhitespaceAndRootLocaleExpansion() {
        var scheme = scheme("TRIM_UPPERCASE_V1", "EC_NATIONAL_ID_V1", IdentifierSchemeStatus.RETIRED);
        var selector = policy.select(scheme, "\u2003é%_ a \t");
        assertTrue(selector.matchesPrefix("É%_ A-123"));
        assertFalse(selector.matchesPrefix("XÉ%_ A-123"));
        assertFalse(selector.matchesPrefix("E%_ A-123"));
        assertFalse(selector.matchesPrefix("ÉZZ A-123"));
        assertFalse(selector.matchesPrefix("É%_A-123"));
        var expanding = policy.select(scheme, "ß".repeat(64));
        assertEquals(128, expanding.normalizedValue().length());
        assertTrue(expanding.matchesPrefix("SS".repeat(64) + "TAIL"));
        assertTrue(expanding.matchesExact("SS".repeat(64)));
    }

    @Test
    void rejectsUnsupportedNormalizationAndRedactsDiagnosticRepresentations() {
        var scheme = scheme("UNKNOWN_NORMALIZER", "ALPHANUMERIC_V1", IdentifierSchemeStatus.DRAFT);
        assertThrows(DomainValidationException.class, () -> policy.select(scheme, "PRIVATE"));
        var supported = scheme("TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", IdentifierSchemeStatus.ACTIVE);
        var selector = policy.select(supported, " private ");
        assertEquals("IdentifierSearchSelector[redacted]", selector.toString());
        assertFalse(selector.matchesExact("***ATE"));
        assertFalse(selector.matchesExact("PRIV"));
    }

    private static IdentifierScheme scheme(String normalizer, String validator, IdentifierSchemeStatus status) {
        return new IdentifierScheme(new IdentifierSchemeId(UUID.fromString("11111111-1111-4111-8111-111111111111")),
                "SEARCH", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.LEGAL_ENTITY,
                "Synthetic search scheme", null, normalizer, validator, 10, 10, true, status,
                IdentifierSchemeVersion.initial(), AuditInfo.initial(Instant.EPOCH, "test"));
    }
}
