package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.PatchIdentifierSchemeCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeChanges;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies ordered correction admission, retained-bound validation, original audit, and accepted legacy metadata edits. */
class PatchIdentifierSchemeUseCaseTest {
    private static final Duration WAIT = Duration.ofSeconds(2);
    private static final RequestMetadata METADATA = new RequestMetadata(new TenantId(UUID.randomUUID()), "editor", UUID.randomUUID());

    @ParameterizedTest
    @EnumSource(value = IdentifierSchemeStatus.class, names = {"DRAFT", "ACTIVE", "DEPRECATED"})
    void identicalNameAdvancesExactlyOnceAndRetainsIdentityCreationAuditAndHistoricalRules(IdentifierSchemeStatus status) {
        var current = scheme(status, 4, status != IdentifierSchemeStatus.DRAFT);
        var port = stored(current);
        var changes = name(current.name());
        var outcome = useCase(port, current).execute(command(current, changes)).await().atMost(WAIT);
        assertEquals(IdentifierSchemeMutationOutcome.Disposition.APPLIED, outcome.disposition());
        var accepted = outcome.scheme().toAggregate();
        assertEquals(5, accepted.version().value());
        assertEquals(current.id(), accepted.id());
        assertEquals(current.code(), accepted.code());
        assertEquals(current.status(), accepted.status());
        assertEquals(current.normalizerKey(), accepted.normalizerKey());
        assertEquals(current.validatorKey(), accepted.validatorKey());
        assertEquals(current.auditInfo().createdAt(), accepted.auditInfo().createdAt());
        assertEquals(current.auditInfo().createdBy(), accepted.auditInfo().createdBy());
        assertTrue(accepted.auditInfo().updatedAt().isAfter(current.auditInfo().updatedAt()));
        assertEquals("editor", accepted.auditInfo().updatedBy());
        assertEquals(List.of("begin", "scheme", "write", "commit", "close"), port.calls);
        assertTrue(port.completions.isEmpty());
    }

    @Test
    void explicitNullClearsDescriptionAndMinimumWhileOmittedMaximumIsRetained() {
        var current = scheme(IdentifierSchemeStatus.DRAFT, 0, false);
        var port = stored(current);
        var changes = new IdentifierSchemeChanges(FieldUpdate.absent(), FieldUpdate.present(null), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.present(null), FieldUpdate.absent(), FieldUpdate.absent());
        var result = useCase(port, current).execute(command(current, changes)).await().atMost(WAIT).scheme();
        assertEquals(null, result.description());
        assertEquals(null, result.minimumLength());
        assertEquals(20, result.maximumLength());
        assertEquals(1, result.version().value());
    }

    @Test
    void absencePrecedesVersionStateAndArbitraryIntegralConfiguration() {
        var current = scheme(IdentifierSchemeStatus.RETIRED, 2, false);
        var port = new IdentifierSchemeMutationPortStub();
        var command = new PatchIdentifierSchemeCommand(METADATA, new IdentifierSchemeId(UUID.randomUUID()),
                new IdentifierSchemeVersion(0), invalidBound());
        assertFailure(port, current, command, ApplicationFailure.IdentifierSchemeNotFound.class);
        assertTrue(port.schemes.isEmpty());
    }

    @Test
    void staleVersionPrecedesRetirementAndConfiguration() {
        var current = scheme(IdentifierSchemeStatus.RETIRED, 2, false);
        var port = stored(current);
        assertFailure(port, current, new PatchIdentifierSchemeCommand(METADATA, current.id(), new IdentifierSchemeVersion(0), invalidBound()),
                ApplicationFailure.IdentifierSchemeVersionMismatch.class);
    }

    @ParameterizedTest
    @EnumSource(value = IdentifierSchemeStatus.class, names = {"ACTIVE", "DEPRECATED", "RETIRED"})
    void stateRestrictionPrecedesExhaustionAndInvalidBounds(IdentifierSchemeStatus status) {
        var current = scheme(status, Long.MAX_VALUE, false);
        var port = stored(current);
        assertFailure(port, current, command(current, invalidBound()), status == IdentifierSchemeStatus.RETIRED
                ? ApplicationFailure.IdentifierSchemeRetired.class : ApplicationFailure.IdentifierSchemeRulesLocked.class);
    }

    @Test
    void exhaustionPrecedesPermittedDraftConfigurationEvaluation() {
        var current = scheme(IdentifierSchemeStatus.DRAFT, Long.MAX_VALUE, false);
        assertFailure(stored(current), current, command(current, invalidBound()), ApplicationFailure.IdentifierSchemeVersionExhausted.class);
    }

    @Test
    void draftRangeChecksUseRetainedMinimumAndRejectArbitraryIntegralOverflowWithoutWrite() {
        var current = scheme(IdentifierSchemeStatus.DRAFT, 0, false);
        var retainedConflict = new IdentifierSchemeChanges(FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.present(BigInteger.valueOf(5)), FieldUpdate.absent());
        assertFailure(stored(current), current, command(current, retainedConflict), ApplicationFailure.IdentifierSchemeLengthRangeInvalid.class);
        assertFailure(stored(current), current, command(current, invalidBound()), ApplicationFailure.IdentifierSchemeLengthRangeInvalid.class);
    }

    @Test
    void draftDescriptiveEditStillRequiresSupportedResultingRules() {
        var current = scheme(IdentifierSchemeStatus.DRAFT, 0, true);
        assertFailure(stored(current), current, command(current, name("New name")), ApplicationFailure.InvalidIdentifierSchemeConfiguration.class);
    }

    private static void assertFailure(IdentifierSchemeMutationPortStub port, IdentifierScheme current,
            PatchIdentifierSchemeCommand command, Class<? extends ApplicationFailure> expected) {
        var execution = useCase(port, current).execute(command).await();
        var failure = assertThrows(ApplicationException.class, () -> execution.atMost(WAIT));
        assertInstanceOf(expected, failure.failure());
        if (port.schemes.containsKey(current.id())) {
            assertEquals(current, port.schemes.get(current.id()));
        }
        assertTrue(port.completions.isEmpty());
        assertEquals(List.of("begin", "scheme", "rollback", "close"), port.calls);
    }

    private static PatchIdentifierSchemeUseCase useCase(IdentifierSchemeMutationPortStub port, IdentifierScheme current) {
        return new PatchIdentifierSchemeUseCase(port, new IdentifierRuleCatalog(),
                Clock.fixed(current.auditInfo().updatedAt().minusSeconds(10), ZoneOffset.UTC));
    }

    private static PatchIdentifierSchemeCommand command(IdentifierScheme current, IdentifierSchemeChanges changes) {
        return new PatchIdentifierSchemeCommand(METADATA, current.id(), current.version(), changes);
    }

    private static IdentifierSchemeMutationPortStub stored(IdentifierScheme scheme) {
        var port = new IdentifierSchemeMutationPortStub();
        port.schemes.put(scheme.id(), scheme);
        return port;
    }

    private static IdentifierSchemeChanges name(String value) {
        return new IdentifierSchemeChanges(FieldUpdate.present(value), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent());
    }

    private static IdentifierSchemeChanges invalidBound() {
        return new IdentifierSchemeChanges(FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.present(new BigInteger("999999999999999999999999999999")), FieldUpdate.absent());
    }

    private static IdentifierScheme scheme(IdentifierSchemeStatus status, long version, boolean obsolete) {
        var base = IdentifierSchemePortContractTest.scheme();
        return new IdentifierScheme(base.id(), base.code(), base.issuingCountryCode(), base.category(), base.applicableSubjectType(),
                base.name(), "Description", obsolete ? "OBSOLETE_NORMALIZER" : base.normalizerKey(),
                obsolete ? "OBSOLETE_VALIDATOR" : base.validatorKey(), 10, 20, false, status,
                new IdentifierSchemeVersion(version), base.auditInfo());
    }
}
