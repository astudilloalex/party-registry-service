package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies active-scheme withdrawal without processing-key revalidation or identity loss. */
class IdentifierSchemeDeprecationTest {

    private static final Instant CREATED = Instant.parse("2026-09-30T00:00:00Z");
    private static final Instant CHANGED = Instant.parse("2026-09-30T00:01:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"TRIM_UPPERCASE_V1", "OBSOLETE_NORMALIZER_V1"})
    void deprecatesAnActiveSchemeWithRetainedConfigurationAndOneRevision(String normalizer) {
        var active = scheme(IdentifierSchemeStatus.ACTIVE, 7, normalizer);
        var deprecated = active.deprecate(CHANGED, "operator");
        assertEquals(new IdentifierScheme(active.id(), active.code(), active.issuingCountryCode(), active.category(),
                active.applicableSubjectType(), active.name(), active.description(), active.normalizerKey(), active.validatorKey(),
                active.minimumLength(), active.maximumLength(), active.requiresExpiration(), IdentifierSchemeStatus.DEPRECATED,
                new IdentifierSchemeVersion(8), new AuditInfo(CREATED, "creator", CHANGED, "operator")), deprecated);
        assertEquals(scheme(IdentifierSchemeStatus.ACTIVE, 7, normalizer), active);
    }

    @ParameterizedTest
    @EnumSource(value = IdentifierSchemeStatus.class, names = {"DRAFT", "DEPRECATED", "RETIRED"})
    void rejectsEveryInvalidSourceStateBeforeExhaustion(IdentifierSchemeStatus status) {
        var invalid = scheme(status, Long.MAX_VALUE, "OBSOLETE_NORMALIZER_V1");
        assertEquals(DomainViolation.IDENTIFIER_SCHEME_DEPRECATION_INVALID_STATE,
                assertThrows(DomainValidationException.class, () -> invalid.deprecate(CHANGED, "operator")).violation());
        assertEquals(scheme(status, Long.MAX_VALUE, "OBSOLETE_NORMALIZER_V1"), invalid);
    }

    @Test
    void rejectsExhaustionWithoutChangingAnOtherwiseEligibleActiveScheme() {
        var active = scheme(IdentifierSchemeStatus.ACTIVE, Long.MAX_VALUE, "OBSOLETE_NORMALIZER_V1");
        assertEquals(DomainViolation.IDENTIFIER_SCHEME_VERSION_OVERFLOW,
                assertThrows(DomainValidationException.class, () -> active.deprecate(CHANGED, "operator")).violation());
        assertEquals(IdentifierSchemeStatus.ACTIVE, active.status());
        assertEquals(AuditInfo.initial(CREATED, "creator"), active.auditInfo());
    }

    private static IdentifierScheme scheme(IdentifierSchemeStatus status, long version, String normalizer) {
        return new IdentifierScheme(new IdentifierSchemeId(UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd994")),
                "CODE", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, "Name", "Description",
                normalizer, "OBSOLETE_VALIDATOR_V1", 1, 10, true, status,
                new IdentifierSchemeVersion(version), AuditInfo.initial(CREATED, "creator"));
    }
}
