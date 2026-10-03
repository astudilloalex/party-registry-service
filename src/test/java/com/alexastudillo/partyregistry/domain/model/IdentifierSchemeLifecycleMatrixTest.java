package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSchemeActivationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies all lifecycle pairs, direct withdrawal, terminal retirement, and retained scheme identity/history. */
class IdentifierSchemeLifecycleMatrixTest {

    private static final Instant CREATED = Instant.parse("2026-09-30T00:00:00Z");
    private static final Instant CHANGED = Instant.parse("2026-09-30T00:01:00Z");
    private final IdentifierSchemeActivationPolicy activation = new IdentifierSchemeActivationPolicy(new IdentifierRuleCatalog());

    @ParameterizedTest
    @MethodSource("matrix")
    void appliesExactlyTheApprovedTwelveStateActionPairs(IdentifierSchemeStatus source, Action action,
            IdentifierSchemeStatus target) {
        var current = scheme(source, 4, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1");
        if (target == null) {
            DomainViolation expected = switch (action) {
                case ACTIVATE -> DomainViolation.IDENTIFIER_SCHEME_ACTIVATION_INVALID_STATE;
                case DEPRECATE -> DomainViolation.IDENTIFIER_SCHEME_DEPRECATION_INVALID_STATE;
                case RETIRE -> DomainViolation.IDENTIFIER_SCHEME_RETIREMENT_INVALID_STATE;
            };
            assertEquals(expected, assertThrows(DomainValidationException.class, () -> apply(current, action)).violation());
        } else {
            var accepted = apply(current, action);
            assertEquals(target, accepted.status());
            assertEquals(5, accepted.version().value());
            assertEquals(new AuditInfo(CREATED, "creator", CHANGED, "operator"), accepted.auditInfo());
        }
        assertEquals(scheme(source, 4, "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1"), current);
    }

    @ParameterizedTest
    @EnumSource(value = IdentifierSchemeStatus.class, names = {"DRAFT", "ACTIVE", "DEPRECATED"})
    void retiresDirectlyWithoutRequiringSupportedRulesOrChangingConfiguration(IdentifierSchemeStatus source) {
        var current = scheme(source, 4, "OBSOLETE_NORMALIZER", "OBSOLETE_VALIDATOR");
        var retired = current.retire(CHANGED, "operator");
        assertEquals(new IdentifierScheme(current.id(), current.code(), current.issuingCountryCode(), current.category(),
                current.applicableSubjectType(), current.name(), current.description(), current.normalizerKey(), current.validatorKey(),
                current.minimumLength(), current.maximumLength(), current.requiresExpiration(), IdentifierSchemeStatus.RETIRED,
                new IdentifierSchemeVersion(5), new AuditInfo(CREATED, "creator", CHANGED, "operator")), retired);
        assertEquals(scheme(source, 4, "OBSOLETE_NORMALIZER", "OBSOLETE_VALIDATOR"), current);
    }

    @Test
    void retiredStateRejectsEveryNewActionBeforeVersionExhaustion() {
        var retired = scheme(IdentifierSchemeStatus.RETIRED, Long.MAX_VALUE, "OBSOLETE_NORMALIZER", "OBSOLETE_VALIDATOR");
        for (Action action : Action.values()) {
            assertThrows(DomainValidationException.class, () -> apply(retired, action));
        }
        assertEquals(DomainViolation.IDENTIFIER_SCHEME_RETIREMENT_INVALID_STATE,
                assertThrows(DomainValidationException.class, () -> retired.retire(CHANGED, "operator")).violation());
        assertEquals(IdentifierSchemeStatus.RETIRED, retired.status());
        assertEquals(Long.MAX_VALUE, retired.version().value());
    }

    @ParameterizedTest
    @EnumSource(value = IdentifierSchemeStatus.class, names = {"DRAFT", "ACTIVE", "DEPRECATED"})
    void exhaustedNonterminalStatesRejectRetirementWithoutMutation(IdentifierSchemeStatus source) {
        var current = scheme(source, Long.MAX_VALUE, "OBSOLETE_NORMALIZER", "OBSOLETE_VALIDATOR");
        assertEquals(DomainViolation.IDENTIFIER_SCHEME_VERSION_OVERFLOW,
                assertThrows(DomainValidationException.class, () -> current.retire(CHANGED, "operator")).violation());
        assertEquals(source, current.status());
        assertEquals(AuditInfo.initial(CREATED, "creator"), current.auditInfo());
    }

    private IdentifierScheme apply(IdentifierScheme current, Action action) {
        return switch (action) {
            case ACTIVATE -> activation.activate(current, CHANGED, "operator");
            case DEPRECATE -> current.deprecate(CHANGED, "operator");
            case RETIRE -> current.retire(CHANGED, "operator");
        };
    }

    private static Stream<Arguments> matrix() {
        return Stream.of(IdentifierSchemeStatus.values()).flatMap(source -> Stream.of(Action.values()).map(action -> {
            IdentifierSchemeStatus target = switch (action) {
                case ACTIVATE -> source == IdentifierSchemeStatus.DRAFT ? IdentifierSchemeStatus.ACTIVE : null;
                case DEPRECATE -> source == IdentifierSchemeStatus.ACTIVE ? IdentifierSchemeStatus.DEPRECATED : null;
                case RETIRE -> source != IdentifierSchemeStatus.RETIRED ? IdentifierSchemeStatus.RETIRED : null;
            };
            return Arguments.of(source, action, target);
        }));
    }

    private static IdentifierScheme scheme(IdentifierSchemeStatus status, long version, String normalizer, String validator) {
        return new IdentifierScheme(new IdentifierSchemeId(UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd993")),
                "Exact_Code", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, "Name", "Description",
                normalizer, validator, 1, 10, true, status, new IdentifierSchemeVersion(version),
                AuditInfo.initial(CREATED, "creator"));
    }

    /** Identifies the three externally requested intents independently of transport types. */
    private enum Action {
        ACTIVATE, DEPRECATE, RETIRE
    }
}
