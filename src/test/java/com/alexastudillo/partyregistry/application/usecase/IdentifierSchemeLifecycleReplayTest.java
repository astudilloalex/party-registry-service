package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.ChangeIdentifierSchemeLifecycleCommand;
import com.alexastudillo.partyregistry.application.command.PatchIdentifierSchemeCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.CompletedIdentifierSchemeOperation;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleAction;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeChanges;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.alexastudillo.partyregistry.application.usecase.ChangeIdentifierSchemeLifecycleUseCaseTest.METADATA;
import static com.alexastudillo.partyregistry.application.usecase.ChangeIdentifierSchemeLifecycleUseCaseTest.WAIT;
import static com.alexastudillo.partyregistry.application.usecase.ChangeIdentifierSchemeLifecycleUseCaseTest.command;
import static com.alexastudillo.partyregistry.application.usecase.ChangeIdentifierSchemeLifecycleUseCaseTest.scheme;
import static com.alexastudillo.partyregistry.application.usecase.ChangeIdentifierSchemeLifecycleUseCaseTest.stored;
import static com.alexastudillo.partyregistry.application.usecase.ChangeIdentifierSchemeLifecycleUseCaseTest.useCase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies action/tenant replay isolation, immutable acceptance, key conflicts before locking, and failed-key reuse. */
class IdentifierSchemeLifecycleReplayTest {
    private static final String KEY = " Exact-key ";

    @ParameterizedTest
    @EnumSource(IdentifierSchemeLifecycleAction.class)
    void keyedSuccessRecordsAcceptedIntentThenReplaysHistoricalResultWithFreshContext(IdentifierSchemeLifecycleAction action) {
        var current = initial(action, false);
        var port = stored(current);
        var command = keyed(current, action, METADATA, KEY);
        var clock = new ChangeIdentifierSchemeLifecycleUseCaseTest.CountingClock(current.auditInfo().updatedAt().plusSeconds(10));
        var useCase = new ChangeIdentifierSchemeLifecycleUseCase(port, new IdentifierRuleCatalog(), clock);
        var first = useCase.execute(command).await().atMost(WAIT);
        assertEquals(IdentifierSchemeMutationOutcome.Disposition.APPLIED, first.disposition());
        assertEquals(List.of("begin", "key", "completion", "scheme", "write", "record", "commit", "close"), port.calls);
        var completion = new CompletedIdentifierSchemeOperation(command.effectiveRequest(), first.scheme());
        var identity = new IdentifierSchemeMutationPortStub.CompletionKey(METADATA.tenantId(), action.operation(), KEY);
        assertEquals(completion, port.completions.get(identity));
        assertEquals(1, port.completions.size());
        var accepted = first.scheme().toAggregate();
        if (action != IdentifierSchemeLifecycleAction.RETIRE) {
            useCase.execute(command(accepted, IdentifierSchemeLifecycleAction.RETIRE)).await().atMost(WAIT);
        }
        var latest = port.schemes.get(current.id());
        var clockReads = clock.reads;
        port.calls.clear();
        var retryMetadata = new RequestMetadata(METADATA.tenantId(), "retry-actor", UUID.randomUUID());
        var retry = useCase.execute(keyed(current, action, retryMetadata, KEY)).await().atMost(WAIT);
        assertEquals(IdentifierSchemeMutationOutcome.Disposition.REPLAYED, retry.disposition());
        assertEquals(first.scheme(), retry.scheme());
        assertEquals(METADATA.userId(), retry.scheme().toAggregate().auditInfo().updatedBy());
        assertEquals(clockReads, clock.reads);
        assertEquals(latest, port.schemes.get(current.id()));
        assertEquals(completion, port.completions.get(identity));
        assertEquals(List.of("begin", "key", "completion", "commit", "close"), port.calls);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void changedTargetOrVersionConflictsBeforeAbsentOrRetiredCurrentChecks(boolean changedTarget) {
        var current = initial(IdentifierSchemeLifecycleAction.ACTIVATE, false);
        var port = stored(current);
        var original = keyed(current, IdentifierSchemeLifecycleAction.ACTIVATE, METADATA, KEY);
        useCase(port, current).execute(original).await().atMost(WAIT);
        var before = port.schemes.get(current.id());
        var saved = java.util.Map.copyOf(port.completions);
        port.calls.clear();
        var another = initial(IdentifierSchemeLifecycleAction.RETIRE, true);
        var conflicting = new ChangeIdentifierSchemeLifecycleCommand(METADATA, changedTarget ? another.id() : current.id(),
                new IdentifierSchemeVersion(changedTarget ? 0 : Long.MAX_VALUE), IdentifierSchemeLifecycleAction.ACTIVATE, Optional.of(KEY));
        var execution = useCase(port, current).execute(conflicting).await();
        var failure = assertThrows(ApplicationException.class, () -> execution.atMost(WAIT));
        assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, failure.failure());
        assertEquals(before, port.schemes.get(current.id()));
        assertEquals(saved, port.completions);
        assertEquals(List.of("begin", "key", "completion", "rollback", "close"), port.calls);
    }

    @Test
    void sameKeyIsIndependentAcrossActionsAndTenantsButCatalogRemainsGlobal() {
        var current = initial(IdentifierSchemeLifecycleAction.ACTIVATE, false);
        var port = stored(current);
        var useCase = useCase(port, current);
        var active = useCase.execute(keyed(current, IdentifierSchemeLifecycleAction.ACTIVATE, METADATA, KEY))
                .await().atMost(WAIT).scheme().toAggregate();
        var otherTenant = new RequestMetadata(new TenantId(UUID.randomUUID()), "another-tenant", UUID.randomUUID());
        port.calls.clear();
        var staleCommand = keyed(current, IdentifierSchemeLifecycleAction.ACTIVATE, otherTenant, KEY);
        var staleExecution = useCase.execute(staleCommand).await();
        var stale = assertThrows(ApplicationException.class, () -> staleExecution.atMost(WAIT));
        assertInstanceOf(ApplicationFailure.IdentifierSchemeVersionMismatch.class, stale.failure());
        assertEquals(List.of("begin", "key", "completion", "scheme", "rollback", "close"), port.calls);
        var deprecated = useCase.execute(keyed(active, IdentifierSchemeLifecycleAction.DEPRECATE, METADATA, KEY))
                .await().atMost(WAIT).scheme().toAggregate();
        var retired = useCase.execute(keyed(deprecated, IdentifierSchemeLifecycleAction.RETIRE, otherTenant, KEY))
                .await().atMost(WAIT).scheme().toAggregate();
        assertEquals(IdentifierSchemeStatus.RETIRED, retired.status());
        assertEquals(3, retired.version().value());
        assertEquals(3, port.completions.size());
        assertEquals(1, port.schemes.size());
    }

    @Test
    void keysAreComparedExactlyWithoutTrimmingOrCaseConversion() {
        var current = initial(IdentifierSchemeLifecycleAction.ACTIVATE, false);
        var port = stored(current);
        var useCase = useCase(port, current);
        useCase.execute(keyed(current, IdentifierSchemeLifecycleAction.ACTIVATE, METADATA, KEY)).await().atMost(WAIT);
        for (var key : List.of(KEY.trim(), KEY.toLowerCase(java.util.Locale.ROOT))) {
            var command = keyed(current, IdentifierSchemeLifecycleAction.ACTIVATE, METADATA, key);
            var execution = useCase.execute(command).await();
            var failure = assertThrows(ApplicationException.class, () -> execution.atMost(WAIT));
            assertInstanceOf(ApplicationFailure.IdentifierSchemeVersionMismatch.class, failure.failure());
        }
        assertEquals(1, port.completions.size());
    }

    @Test
    void failedActivationKeyRemainsReusableAfterRealDraftRepair() {
        var current = initial(IdentifierSchemeLifecycleAction.ACTIVATE, true);
        var port = stored(current);
        var useCase = useCase(port, current);
        var activationCommand = keyed(current, IdentifierSchemeLifecycleAction.ACTIVATE, METADATA, KEY);
        var activationExecution = useCase.execute(activationCommand).await();
        var failure = assertThrows(ApplicationException.class, () -> activationExecution.atMost(WAIT));
        assertInstanceOf(ApplicationFailure.InvalidIdentifierSchemeConfiguration.class, failure.failure());
        assertEquals(current, port.schemes.get(current.id()));
        assertTrue(port.completions.isEmpty());
        var changes = new IdentifierSchemeChanges(FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.present("TRIM_UPPERCASE_V1"),
                FieldUpdate.present("ALPHANUMERIC_V1"), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent());
        var repaired = new PatchIdentifierSchemeUseCase(port, new IdentifierRuleCatalog(), Clock.fixed(current.auditInfo().updatedAt(), ZoneOffset.UTC))
                .execute(new PatchIdentifierSchemeCommand(METADATA, current.id(), current.version(), changes)).await().atMost(WAIT).scheme().toAggregate();
        var accepted = useCase.execute(keyed(repaired, IdentifierSchemeLifecycleAction.ACTIVATE, METADATA, KEY)).await().atMost(WAIT);
        assertEquals(IdentifierSchemeMutationOutcome.Disposition.APPLIED, accepted.disposition());
        assertEquals(2, accepted.scheme().version().value());
        assertEquals(1, port.completions.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completionOrAcceptanceFailureRollsBackEverythingAndDoesNotConsumeKey(boolean acceptanceFailure) {
        var current = initial(IdentifierSchemeLifecycleAction.ACTIVATE, false);
        var port = stored(current);
        var original = new IllegalStateException("Injected acceptance failure");
        if (acceptanceFailure) {
            port.acceptanceFailure = original;
        } else {
            port.completionFailure = original;
        }
        var command = keyed(current, IdentifierSchemeLifecycleAction.ACTIVATE, METADATA, KEY);
        var useCase = useCase(port, current);
        var execution = useCase.execute(command).await();
        assertSame(original, assertThrows(IllegalStateException.class, () -> execution.atMost(WAIT)));
        assertEquals(current, port.schemes.get(current.id()));
        assertTrue(port.completions.isEmpty());
        assertEquals(List.of("begin", "key", "completion", "scheme", "write", "record", "rollback", "close"), port.calls);
        port.acceptanceFailure = null;
        port.completionFailure = null;
        assertEquals(IdentifierSchemeMutationOutcome.Disposition.APPLIED, useCase.execute(command).await().atMost(WAIT).disposition());
    }

    @Test
    void invalidTypedIntentCannotReachCompletedLookup() {
        var current = initial(IdentifierSchemeLifecycleAction.ACTIVATE, false);
        var port = stored(current);
        for (var invalid : List.of(" ", "x".repeat(129))) {
            assertThrows(IllegalArgumentException.class, () -> keyed(current, IdentifierSchemeLifecycleAction.ACTIVATE, METADATA, invalid));
        }
        var currentVersion = current.version();
        var keyOptional = Optional.of(KEY);
        assertThrows(NullPointerException.class, () -> new ChangeIdentifierSchemeLifecycleCommand(METADATA, null,
                currentVersion, IdentifierSchemeLifecycleAction.ACTIVATE, keyOptional));
        assertThrows(DomainValidationException.class, () -> new IdentifierSchemeVersion(-1));
        assertTrue(port.calls.isEmpty());
    }

    private static IdentifierScheme initial(IdentifierSchemeLifecycleAction action, boolean obsolete) {
        return scheme(action == IdentifierSchemeLifecycleAction.DEPRECATE ? IdentifierSchemeStatus.ACTIVE : IdentifierSchemeStatus.DRAFT,
                0, obsolete, 20);
    }

    private static ChangeIdentifierSchemeLifecycleCommand keyed(IdentifierScheme scheme, IdentifierSchemeLifecycleAction action,
            RequestMetadata metadata, String key) {
        return new ChangeIdentifierSchemeLifecycleCommand(metadata, scheme.id(), scheme.version(), action, Optional.of(key));
    }
}
