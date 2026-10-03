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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies supported configuration by exact keys while keeping historical model restoration independent. */
class IdentifierRuleCatalogConfigurationTest {

    private final IdentifierRuleCatalog catalog = new IdentifierRuleCatalog();

    @ParameterizedTest
    @ValueSource(strings = {"ALPHANUMERIC_V1", "EC_NATIONAL_ID_V1", "EC_TAX_ID_V1"})
    void admitsEachSupportedValidatorWithoutACompleteIdentifierValue(String validator) {
        assertDoesNotThrow(() -> catalog.requireSupportedKeys("TRIM_UPPERCASE_V1", validator));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "UNKNOWN", "trim_uppercase_v1", " TRIM_UPPERCASE_V1", "TRIM_UPPERCASE_V1 "})
    void rejectsNonmatchingNormalizerKeys(String normalizer) {
        assertEquals(DomainViolation.IDENTIFIER_RULE_CATALOG_INVALID,
                assertThrows(DomainValidationException.class,
                        () -> catalog.requireSupportedKeys(normalizer, "ALPHANUMERIC_V1")).violation());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "UNKNOWN", "alphanumeric_v1", " ALPHANUMERIC_V1", "ALPHANUMERIC_V1 "})
    void rejectsNonmatchingValidatorKeys(String validator) {
        assertEquals(DomainViolation.IDENTIFIER_RULE_CATALOG_INVALID,
                assertThrows(DomainValidationException.class,
                        () -> catalog.requireSupportedKeys("TRIM_UPPERCASE_V1", validator)).violation());
    }

    @Test
    void missingKeysProduceNeutralViolationsInsteadOfNullPointerFailures() {
        assertEquals(DomainViolation.IDENTIFIER_RULE_CATALOG_INVALID,
                assertThrows(DomainValidationException.class,
                        () -> catalog.requireSupportedKeys(null, "ALPHANUMERIC_V1")).violation());
        assertEquals(DomainViolation.IDENTIFIER_RULE_CATALOG_INVALID,
                assertThrows(DomainValidationException.class,
                        () -> catalog.requireSupportedKeys("TRIM_UPPERCASE_V1", null)).violation());
    }

    @Test
    void restoresEveryHistoricalStateWithoutRequiringCurrentlySupportedKeys() {
        for (IdentifierSchemeStatus status : IdentifierSchemeStatus.values()) {
            var restored = new IdentifierScheme(new IdentifierSchemeId(UUID.randomUUID()), "HISTORICAL", "EC",
                    IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, "Historical configuration", null,
                    "RETIRED_NORMALIZER_V1", "RETIRED_VALIDATOR_V1", null, null, true, status,
                    IdentifierSchemeVersion.initial(), AuditInfo.initial(Instant.parse("2026-09-30T00:00:00Z"), "catalog"));
            assertEquals(status, restored.status());
            assertEquals("RETIRED_NORMALIZER_V1", restored.normalizerKey());
            assertEquals("RETIRED_VALIDATOR_V1", restored.validatorKey());
        }
    }
}
