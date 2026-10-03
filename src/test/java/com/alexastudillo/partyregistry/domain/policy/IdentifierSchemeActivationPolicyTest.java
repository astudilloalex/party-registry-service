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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies activation state, exhaustion, eligibility, and audit without transport or persistence dependencies. */
class IdentifierSchemeActivationPolicyTest {

    private static final Instant CREATED = Instant.parse("2026-09-30T00:00:00Z");
    private static final Instant ACTIVATED = Instant.parse("2026-09-30T00:01:00Z");
    private final IdentifierSchemeActivationPolicy policy = new IdentifierSchemeActivationPolicy(new IdentifierRuleCatalog());

    @ParameterizedTest
    @ValueSource(strings = {"ALPHANUMERIC_V1", "EC_NATIONAL_ID_V1", "EC_TAX_ID_V1"})
    void activatesAValidDraftExactlyOnceWithoutChangingConfiguration(String validator) {
        var draft = scheme(IdentifierSchemeStatus.DRAFT, 0, "TRIM_UPPERCASE_V1", validator, 10);
        var active = policy.activate(draft, ACTIVATED, "activator");
        assertEquals(new IdentifierScheme(draft.id(), draft.code(), draft.issuingCountryCode(), draft.category(),
                draft.applicableSubjectType(), draft.name(), draft.description(), draft.normalizerKey(), draft.validatorKey(),
                draft.minimumLength(), draft.maximumLength(), draft.requiresExpiration(), IdentifierSchemeStatus.ACTIVE,
                new IdentifierSchemeVersion(1), new AuditInfo(CREATED, "creator", ACTIVATED, "activator")), active);
        assertEquals(scheme(IdentifierSchemeStatus.DRAFT, 0, "TRIM_UPPERCASE_V1", validator, 10), draft);
    }

    @ParameterizedTest
    @EnumSource(value = IdentifierSchemeStatus.class, names = {"ACTIVE", "DEPRECATED", "RETIRED"})
    void rejectsOtherStatesBeforeExhaustionOrUnsupportedConfiguration(IdentifierSchemeStatus status) {
        var invalid = scheme(status, Long.MAX_VALUE, "OBSOLETE", "OBSOLETE", 10);
        assertEquals(DomainViolation.IDENTIFIER_SCHEME_ACTIVATION_INVALID_STATE,
                assertThrows(DomainValidationException.class,
                        () -> policy.activate(invalid, ACTIVATED, "activator")).violation());
    }

    @Test
    void exhaustionPrecedesEligibilityWithoutChangingTheDraft() {
        var draft = scheme(IdentifierSchemeStatus.DRAFT, Long.MAX_VALUE, "OBSOLETE", "OBSOLETE", 10);
        assertEquals(DomainViolation.IDENTIFIER_SCHEME_VERSION_OVERFLOW,
                assertThrows(DomainValidationException.class,
                        () -> policy.activate(draft, ACTIVATED, "activator")).violation());
        assertEquals(scheme(IdentifierSchemeStatus.DRAFT, Long.MAX_VALUE, "OBSOLETE", "OBSOLETE", 10), draft);
    }

    @Test
    void rejectsUnsupportedRulesWithoutMutatingStoredDraftValues() {
        for (var draft : new IdentifierScheme[] {
                scheme(IdentifierSchemeStatus.DRAFT, 4, "OBSOLETE", "ALPHANUMERIC_V1", 10),
                scheme(IdentifierSchemeStatus.DRAFT, 4, "TRIM_UPPERCASE_V1", "OBSOLETE", 10)}) {
            assertEquals(DomainViolation.IDENTIFIER_RULE_CATALOG_INVALID,
                    assertThrows(DomainValidationException.class,
                            () -> policy.activate(draft, ACTIVATED, "activator")).violation());
            assertEquals(IdentifierSchemeStatus.DRAFT, draft.status());
            assertEquals(4, draft.version().value());
            assertEquals(AuditInfo.initial(CREATED, "creator"), draft.auditInfo());
        }
    }

    @Test
    void rejectsOversizedHistoricalBoundsAsAnEligibilityFailure() {
        var draft = scheme(IdentifierSchemeStatus.DRAFT, 4, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", 32768);
        assertEquals(DomainViolation.IDENTIFIER_MAXIMUM_LENGTH_INVALID,
                assertThrows(DomainValidationException.class,
                        () -> policy.activate(draft, ACTIVATED, "activator")).violation());
        assertEquals(IdentifierSchemeStatus.DRAFT, draft.status());
        assertEquals(4, draft.version().value());
    }

    private static IdentifierScheme scheme(IdentifierSchemeStatus status, long version, String normalizer,
            String validator, int maximumLength) {
        return new IdentifierScheme(new IdentifierSchemeId(UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd995")),
                "Exact_Code", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, "Name", "Description",
                normalizer, validator, 1, maximumLength, true, status, new IdentifierSchemeVersion(version),
                AuditInfo.initial(CREATED, "creator"));
    }
}
