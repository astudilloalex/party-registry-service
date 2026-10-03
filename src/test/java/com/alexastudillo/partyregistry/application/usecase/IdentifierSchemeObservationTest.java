package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.ChangeIdentifierSchemeLifecycleCommand;
import com.alexastudillo.partyregistry.application.command.CreateIdentifierSchemeCommand;
import com.alexastudillo.partyregistry.application.command.PatchIdentifierSchemeCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeCreateInput;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleAction;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageSlice;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchCriteria;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchScope;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSelector;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeCursorPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeReadPort;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.query.GetIdentifierSchemeQuery;
import com.alexastudillo.partyregistry.application.query.ListIdentifierSchemesQuery;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeChanges;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import com.alexastudillo.partyregistry.support.RecordingOperationObserver;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.alexastudillo.partyregistry.application.usecase.ChangeIdentifierSchemeLifecycleUseCaseTest.METADATA;
import static com.alexastudillo.partyregistry.application.usecase.ChangeIdentifierSchemeLifecycleUseCaseTest.WAIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Preserves subscription-scoped scheme results, errors, cancellation, replay context, and telemetry isolation. */
class IdentifierSchemeObservationTest {
    @ParameterizedTest
    @EnumSource(Route.class)
    void observesEveryRouteLazilyOncePerSubscriptionWithBoundedOutcomes(Route route) {
        var fixture = new Fixture();
        var observer = new RecordingOperationObserver();
        var pipeline = fixture.execute(route, observer);
        assertTrue(observer.startedCalls().isEmpty());
        assertEquals(0, fixture.calls.get());
        for (int i = 0; i < 2; i++) {
            var item = pipeline.await().atMost(WAIT);
            if (route.mutation()) {
                assertSame(fixture.result, item);
            }
        }
        assertEquals(2, fixture.calls.get());
        assertEquals(List.of(new RecordingOperationObserver.StartedCall(METADATA, route.operation()),
                new RecordingOperationObserver.StartedCall(METADATA, route.operation())), observer.startedCalls());
        var expected = new RecordingOperationObserver.CompletedCall(METADATA, route.operation(),
                route.mutation() ? OperationOutcome.APPLIED : OperationOutcome.RETRIEVED);
        assertEquals(List.of(expected, expected), observer.completedCalls());
    }

    @ParameterizedTest
    @EnumSource(value = Route.class, names = {"CREATE", "ACTIVATE", "DEPRECATE", "RETIRE"})
    void observesReplayedDispositionWithoutInferringItFromCurrentState(Route route) {
        var fixture = new Fixture();
        fixture.result = new IdentifierSchemeMutationOutcome(fixture.result.scheme(), IdentifierSchemeMutationOutcome.Disposition.REPLAYED);
        var observer = new RecordingOperationObserver();
        assertSame(fixture.result, fixture.execute(route, observer).await().atMost(WAIT));
        assertEquals(List.of(new RecordingOperationObserver.CompletedCall(METADATA, route.operation(), OperationOutcome.REPLAYED)),
                observer.completedCalls());
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void preservesDependencyFailuresAndDownstreamCancellationForEveryRoute(Route route) {
        var fixture = new Fixture();
        var original = new ApplicationException(new ApplicationFailure.DependencyUnavailable("catalog"));
        fixture.failure = original;
        var observer = new RecordingOperationObserver();
        fixture.execute(route, observer).subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure(actual -> assertSame(original, actual), WAIT).assertFailed();
        assertEquals(List.of(new RecordingOperationObserver.CompletedCall(METADATA, route.operation(), OperationOutcome.DEPENDENCY_UNAVAILABLE)),
                observer.completedCalls());
        fixture.failure = null;
        fixture.pending = true;
        var cancellationObserver = new RecordingOperationObserver();
        var subscriber = fixture.execute(route, cancellationObserver).subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitSubscription(WAIT);
        assertTrue(cancellationObserver.completedCalls().isEmpty());
        subscriber.cancel();
        subscriber.cancel();
        assertTrue(fixture.cancelled.get());
        assertEquals(2, fixture.calls.get());
        assertEquals(List.of(new RecordingOperationObserver.CompletedCall(METADATA, route.operation(), OperationOutcome.CANCELLED)),
                cancellationObserver.completedCalls());
    }

    @ParameterizedTest
    @MethodSource("failures")
    void observesEverySchemeFailureAndPreservesOriginalThrowable(RuntimeException failure, OperationOutcome expected) {
        var fixture = new Fixture();
        fixture.failure = failure;
        var observer = new RecordingOperationObserver();
        fixture.execute(Route.ACTIVATE, observer).subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure(actual -> assertSame(failure, actual), WAIT).assertFailed();
        assertEquals(List.of(new RecordingOperationObserver.CompletedCall(METADATA, Route.ACTIVATE.operation(), expected)),
                observer.completedCalls());
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void telemetryStartOrCompletionFailureCannotReplaceSuccessErrorOrCancellation(Route route) {
        for (boolean failAtStart : List.of(false, true)) {
            var fixture = new Fixture();
            OperationObservationPort broken = (metadata, operation) -> {
                if (failAtStart) {
                    throw new IllegalStateException("Injected observer start failure");
                }
                return outcome -> { throw new IllegalStateException("Injected observer completion failure"); };
            };
            var item = fixture.execute(route, broken).await().atMost(WAIT);
            if (route.mutation()) {
                assertSame(fixture.result, item);
            }
            var original = new IllegalStateException("Original business failure");
            fixture.failure = original;
            var execution = fixture.execute(route, broken).await();
            assertSame(original, assertThrows(IllegalStateException.class, () -> execution.atMost(WAIT)));
            fixture.failure = null;
            fixture.pending = true;
            fixture.execute(route, broken).subscribe().withSubscriber(UniAssertSubscriber.create()).awaitSubscription(WAIT).cancel();
            assertTrue(fixture.cancelled.get());
            assertEquals(3, fixture.calls.get());
        }
    }

    @Test
    void realMutationObservationCompletesAfterCommitAndUsesRetryContextWithOriginalAudit() {
        var current = ChangeIdentifierSchemeLifecycleUseCaseTest.scheme(IdentifierSchemeStatus.DRAFT, 0, false, 20);
        var port = ChangeIdentifierSchemeLifecycleUseCaseTest.stored(current);
        var observer = new RecordingOperationObserver();
        OperationObservationPort afterAcceptance = (metadata, operation) -> {
            var handle = observer.start(metadata, operation);
            return outcome -> {
                assertTrue(port.calls.contains("commit"));
                assertEquals("close", port.calls.getLast());
                handle.complete(outcome);
                throw new IllegalStateException("Telemetry lost after acceptance");
            };
        };
        var clock = Clock.fixed(current.auditInfo().updatedAt(), ZoneOffset.UTC);
        var useCase = new ChangeIdentifierSchemeLifecycleUseCase(port, new IdentifierRuleCatalog(), clock, afterAcceptance);
        var command = new ChangeIdentifierSchemeLifecycleCommand(METADATA, current.id(), current.version(),
                IdentifierSchemeLifecycleAction.ACTIVATE, Optional.of("key"));
        var accepted = useCase.execute(command).await().atMost(WAIT);
        var retryContext = new RequestMetadata(METADATA.tenantId(), "retry-actor", UUID.randomUUID());
        port.calls.clear();
        var replayed = useCase.execute(new ChangeIdentifierSchemeLifecycleCommand(retryContext, current.id(), current.version(),
                IdentifierSchemeLifecycleAction.ACTIVATE, Optional.of("key"))).await().atMost(WAIT);
        assertEquals(accepted.scheme(), replayed.scheme());
        assertEquals(METADATA.userId(), replayed.scheme().toAggregate().auditInfo().updatedBy());
        assertEquals(List.of(new RecordingOperationObserver.CompletedCall(METADATA, Route.ACTIVATE.operation(), OperationOutcome.APPLIED),
                new RecordingOperationObserver.CompletedCall(retryContext, Route.ACTIVATE.operation(), OperationOutcome.REPLAYED)), observer.completedCalls());
    }

    @Test
    void domainTranslationOccursInsideObservationAndRejectedMutationRemainsUnaccepted() {
        var current = ChangeIdentifierSchemeLifecycleUseCaseTest.scheme(IdentifierSchemeStatus.DRAFT, 0, true, 20);
        var port = ChangeIdentifierSchemeLifecycleUseCaseTest.stored(current);
        var observer = new RecordingOperationObserver();
        var useCase = new ChangeIdentifierSchemeLifecycleUseCase(port, new IdentifierRuleCatalog(),
                Clock.fixed(current.auditInfo().updatedAt(), ZoneOffset.UTC), observer);
        var command = ChangeIdentifierSchemeLifecycleUseCaseTest.command(
                current, IdentifierSchemeLifecycleAction.ACTIVATE);
        var execution = useCase.execute(command).await();
        var failure = assertThrows(ApplicationException.class, () -> execution.atMost(WAIT));
        assertInstanceOf(ApplicationFailure.InvalidIdentifierSchemeConfiguration.class, failure.failure());
        assertEquals(List.of(new RecordingOperationObserver.CompletedCall(METADATA, Route.ACTIVATE.operation(), OperationOutcome.VALIDATION_FAILED)),
                observer.completedCalls());
        assertEquals(current, port.schemes.get(current.id()));
        assertTrue(port.completions.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = Route.class, names = {"CREATE", "PATCH", "ACTIVATE"})
    void preservesDomainTranslationWhenTheMutationBoundaryThrowsSynchronously(Route route) {
        var fixture = new Fixture();
        fixture.failure = new DomainValidationException(DomainViolation.IDENTIFIER_RULE_CATALOG_INVALID, "Unsupported rules");
        fixture.synchronousFailure = true;
        var observer = new RecordingOperationObserver();
        var execution = fixture.execute(route, observer).await();
        var failure = assertThrows(ApplicationException.class, () -> execution.atMost(WAIT));
        assertInstanceOf(ApplicationFailure.InvalidIdentifierSchemeConfiguration.class, failure.failure());
        assertEquals(List.of(new RecordingOperationObserver.CompletedCall(METADATA, route.operation(), OperationOutcome.VALIDATION_FAILED)),
                observer.completedCalls());
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> failures() {
        return Stream.of(
                failure(new ApplicationFailure.IdentifierSchemeNotFound(), OperationOutcome.NOT_FOUND),
                failure(new ApplicationFailure.IdentifierSchemeVersionMismatch(), OperationOutcome.PRECONDITION_FAILED),
                failure(new ApplicationFailure.IdentifierSchemeCodeConflict(), OperationOutcome.CONFLICT),
                failure(new ApplicationFailure.IdentifierSchemeRulesLocked(), OperationOutcome.CONFLICT),
                failure(new ApplicationFailure.IdentifierSchemeRetired(), OperationOutcome.CONFLICT),
                failure(new ApplicationFailure.InvalidIdentifierSchemeLifecycle(), OperationOutcome.CONFLICT),
                failure(new ApplicationFailure.IdentifierSchemeVersionExhausted(), OperationOutcome.CONFLICT),
                failure(new ApplicationFailure.IdentifierSchemeLengthRangeInvalid(), OperationOutcome.VALIDATION_FAILED),
                failure(new ApplicationFailure.InvalidIdentifierSchemeConfiguration(), OperationOutcome.VALIDATION_FAILED),
                failure(new ApplicationFailure.InvalidIdentifierSchemeCursor(), OperationOutcome.VALIDATION_FAILED),
                failure(new ApplicationFailure.IdempotencyKeyConflict("key"), OperationOutcome.CONFLICT),
                org.junit.jupiter.params.provider.Arguments.of(new IllegalStateException("Unexpected"), OperationOutcome.INTERNAL_FAILURE),
                org.junit.jupiter.params.provider.Arguments.of(new CancellationException("Cancelled"), OperationOutcome.CANCELLED));
    }

    private static org.junit.jupiter.params.provider.Arguments failure(ApplicationFailure failure, OperationOutcome outcome) {
        return org.junit.jupiter.params.provider.Arguments.of(new ApplicationException(failure), outcome);
    }

    /** Enumerates the eight delivery operations mapped to seven bounded Application observations. */
    enum Route {
        CREATE, GET_ID, GET_CODE, LIST, PATCH, ACTIVATE, DEPRECATE, RETIRE;

        boolean mutation() { return this != GET_ID && this != GET_CODE && this != LIST; }

        ObservedOperation operation() {
            return switch (this) {
                case CREATE -> ObservedOperation.APPLICATION_IDENTIFIER_SCHEME_CREATION;
                case GET_ID, GET_CODE -> ObservedOperation.APPLICATION_IDENTIFIER_SCHEME_RETRIEVAL;
                case LIST -> ObservedOperation.APPLICATION_IDENTIFIER_SCHEME_LIST;
                case PATCH -> ObservedOperation.APPLICATION_IDENTIFIER_SCHEME_PATCH;
                case ACTIVATE -> ObservedOperation.APPLICATION_IDENTIFIER_SCHEME_ACTIVATION;
                case DEPRECATE -> ObservedOperation.APPLICATION_IDENTIFIER_SCHEME_DEPRECATION;
                case RETIRE -> ObservedOperation.APPLICATION_IDENTIFIER_SCHEME_RETIREMENT;
            };
        }
    }

    /** Controls cold adapter terminal signals without simulating business orchestration or telemetry technology. */
    private static final class Fixture implements IdentifierSchemeReadPort, IdentifierSchemeCursorPort {
        final IdentifierSchemeResult scheme = IdentifierSchemeResult.fromAggregate(IdentifierSchemePortContractTest.scheme());
        IdentifierSchemeMutationOutcome result = new IdentifierSchemeMutationOutcome(scheme, IdentifierSchemeMutationOutcome.Disposition.APPLIED);
        final AtomicInteger calls = new AtomicInteger();
        final AtomicBoolean cancelled = new AtomicBoolean();
        @Nullable RuntimeException failure;
        boolean pending;
        boolean synchronousFailure;

        Uni<?> execute(Route route, OperationObservationPort observer) {
            IdentifierSchemeMutationPort mutations = (metadata, work) -> {
                var original = failure;
                if (synchronousFailure && original != null) { throw original; }
                return terminal(result);
            };
            var clock = Clock.fixed(scheme.toAggregate().auditInfo().updatedAt(), ZoneOffset.UTC);
            var changes = new IdentifierSchemeChanges(FieldUpdate.present("Name"), FieldUpdate.absent(), FieldUpdate.absent(),
                    FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent());
            return switch (route) {
                case CREATE -> new CreateIdentifierSchemeUseCase(mutations, new IdentifierRuleCatalog(), clock, observer).execute(
                        new CreateIdentifierSchemeCommand(METADATA, "key", new IdentifierSchemeCreateInput(scheme.code(), scheme.issuingCountryCode(),
                                scheme.category(), scheme.applicableSubjectType(), scheme.name(), null, scheme.normalizerKey(), scheme.validatorKey(), null, null, false)));
                case GET_ID, GET_CODE -> new GetIdentifierSchemeUseCase(this, observer).execute(new GetIdentifierSchemeQuery(METADATA,
                        route == Route.GET_ID ? new IdentifierSchemeSelector.ById(scheme.id()) : new IdentifierSchemeSelector.ByCode(scheme.code())));
                case LIST -> new ListIdentifierSchemesUseCase(this, this, observer).execute(new ListIdentifierSchemesQuery(METADATA,
                        new IdentifierSchemeSearchCriteria(null, null, null, null, 50), Optional.empty()));
                case PATCH -> new PatchIdentifierSchemeUseCase(mutations, new IdentifierRuleCatalog(), clock, observer).execute(
                        new PatchIdentifierSchemeCommand(METADATA, scheme.id(), scheme.version(), changes));
                case ACTIVATE, DEPRECATE, RETIRE -> new ChangeIdentifierSchemeLifecycleUseCase(mutations, new IdentifierRuleCatalog(), clock, observer).execute(
                        new ChangeIdentifierSchemeLifecycleCommand(METADATA, scheme.id(), scheme.version(), IdentifierSchemeLifecycleAction.valueOf(route.name()), Optional.empty()));
            };
        }

        private <T> Uni<T> terminal(T item) {
            return Uni.createFrom().deferred(() -> {
                calls.incrementAndGet();
                var original = failure;
                if (original != null) { return Uni.createFrom().failure(original); }
                return pending ? Uni.createFrom().<T>nothing().onCancellation().invoke(() -> cancelled.set(true)) : Uni.createFrom().item(item);
            });
        }

        @Override public Uni<Optional<IdentifierSchemeResult>> findById(IdentifierSchemeId id) { return terminal(Optional.of(scheme)); }
        @Override public Uni<Optional<IdentifierSchemeResult>> findByCode(String code) { return terminal(Optional.of(scheme)); }
        @Override public Uni<IdentifierSchemePageSlice> findPage(IdentifierSchemeSearchCriteria criteria, Optional<IdentifierSchemePageBoundary> boundary) {
            return terminal(new IdentifierSchemePageSlice(List.of(scheme), Optional.empty(), Optional.empty()));
        }
        @Override public String encode(IdentifierSchemePageBoundary boundary, IdentifierSchemeSearchScope scope) { throw new AssertionError("No continuation expected"); }
        @Override public IdentifierSchemePageBoundary decode(String token, IdentifierSchemeSearchScope scope) { throw new AssertionError("No cursor expected"); }
    }
}
