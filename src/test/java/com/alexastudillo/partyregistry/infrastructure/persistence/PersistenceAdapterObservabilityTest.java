package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.LegalEntityRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.NaturalPersonRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.RegistrationFingerprintPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.RecordingOperationObserver;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies concrete transaction adapters observe only subscribed terminal
 * outcomes.
 */
class PersistenceAdapterObservabilityTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final LocalDate TODAY = LocalDate.of(2026, Month.SEPTEMBER, 4);
    private static final Instant NOW = TODAY.atTime(18, 0).toInstant(ZoneOffset.UTC);
    private static final TenantId TENANT_ID = new TenantId(
            UUID.fromString("01991ebd-6800-7000-8000-000000000001"));
    private static final RequestMetadata METADATA = new RequestMetadata(
            TENANT_ID,
            "adapter-observation-user",
            UUID.fromString("01991ebd-6800-7000-8000-000000000002"));
    private static final IdentifierScheme SCHEME = scheme();

    @Test
    void observesNaturalAndLegalRegistrationTransactionsOnlyAfterSubscription() {
        NaturalPersonRegistrationCandidate natural = naturalCandidate();
        PartyRegistrationResult naturalResult = registrationResult(
                natural,
                PartyRegistrationOutcome.CREATED);
        RecordingOperationObserver naturalObserver = new RecordingOperationObserver();
        var naturalAdapter = registrationAdapter(
                sessionFactory(Uni.createFrom().item(naturalResult)),
                naturalObserver);

        assertTrue(naturalObserver.startedCalls().isEmpty());
        Uni<PartyRegistrationResult> naturalOperation = naturalAdapter.registerNaturalPerson(natural);
        assertTrue(naturalObserver.startedCalls().isEmpty());
        assertSame(naturalResult, awaitItem(naturalOperation));
        assertObservation(
                naturalObserver,
                ObservedOperation.TRANSACTION_NATURAL_PERSON_REGISTRATION,
                OperationOutcome.CREATED);

        LegalEntityRegistrationCandidate legal = legalCandidate();
        PartyRegistrationResult legalResult = registrationResult(
                legal,
                PartyRegistrationOutcome.REPLAYED);
        RecordingOperationObserver legalObserver = new RecordingOperationObserver();
        var legalAdapter = registrationAdapter(
                sessionFactory(Uni.createFrom().item(legalResult)),
                legalObserver);

        assertTrue(legalObserver.startedCalls().isEmpty());
        Uni<PartyRegistrationResult> legalOperation = legalAdapter.registerLegalEntity(legal);
        assertTrue(legalObserver.startedCalls().isEmpty());
        assertSame(legalResult, awaitItem(legalOperation));
        assertObservation(
                legalObserver,
                ObservedOperation.TRANSACTION_LEGAL_ENTITY_REGISTRATION,
                OperationOutcome.REPLAYED);
    }

    @Test
    void observesAdditionalIdentifierTransactionOutcomes() {
        PartyIdentifierResult identifierResult = PartyIdentifierResult.fromAggregate(identifier(naturalPerson().partyId()), SCHEME);
        var committed = new PartyIdentifierMutationOutcome(identifierResult, PartyIdentifierMutationOutcome.Disposition.APPLIED);
        RecordingOperationObserver identifierObserver = new RecordingOperationObserver();
        var identifierAdapter = identifierAdapter(committedFactory(Uni.createFrom().item(committed)), identifierObserver);

        assertTrue(identifierObserver.startedCalls().isEmpty());
        Uni<PartyIdentifierMutationOutcome> identifierOperation = identifierAdapter.execute(METADATA, _ -> { throw new AssertionError("Committed factory double must supply its terminal result"); });
        assertTrue(identifierObserver.startedCalls().isEmpty());
        assertSame(committed, awaitItem(identifierOperation));
        assertObservation(
                identifierObserver,
                ObservedOperation.TRANSACTION_PARTY_IDENTIFIER_MUTATION,
                OperationOutcome.APPLIED);

    }

    @Test
    void preservesTypedFailureAndCancellationAtTheAdapterObservationBoundary() {
        NaturalPersonRegistrationCandidate candidate = naturalCandidate();
        ApplicationException conflict = new ApplicationException(
                new ApplicationFailure.IdempotencyKeyConflict("safe-key"));
        RecordingOperationObserver conflictObserver = new RecordingOperationObserver();
        var registrationAdapter = registrationAdapter(
                sessionFactory(Uni.createFrom().failure(conflict)),
                conflictObserver);

        assertSame(conflict, awaitFailure(registrationAdapter.registerNaturalPerson(candidate)));
        assertObservation(
                conflictObserver,
                ObservedOperation.TRANSACTION_NATURAL_PERSON_REGISTRATION,
                OperationOutcome.CONFLICT);

        AtomicBoolean sourceCancelled = new AtomicBoolean();
        RecordingOperationObserver cancellationObserver = new RecordingOperationObserver();
        var cancellationAdapter = registrationAdapter(
                sessionFactory(Uni.createFrom().nothing()
                         .onCancellation().invoke(() -> sourceCancelled.set(true))),
                cancellationObserver);
        UniAssertSubscriber<PartyRegistrationResult> subscriber = cancellationAdapter
                .registerNaturalPerson(candidate)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.awaitSubscription(TIMEOUT).cancel();

        assertTrue(sourceCancelled.get());
        assertObservation(
                cancellationObserver,
                ObservedOperation.TRANSACTION_NATURAL_PERSON_REGISTRATION,
                OperationOutcome.CANCELLED);
    }

    private static HibernateReactiveIdempotentPartyRegistrationAdapter registrationAdapter(
            Mutiny.SessionFactory sessionFactory,
            RecordingOperationObserver observer) {
        PartyRegistrationIdempotencyPersistence idempotencyPersistence = new PartyRegistrationIdempotencyPersistence(
                sessionFactory,
                new FingerprintDouble(),
                new IdempotencyResultSnapshotCodec());
        return new HibernateReactiveIdempotentPartyRegistrationAdapter(
                sessionFactory,
                new NaturalPersonPersistenceMapper(),
                new LegalEntityPersistenceMapper(),
                new IdentifierSchemePersistenceMapper(),
                new PartyIdentifierPersistenceMapper(),
                new PartyOutboxEventPersistenceMapper(),
                idempotencyPersistence,
                observer,
                "disabled");
    }

    private static Mutiny.SessionFactory sessionFactory(Uni<?> outcome) {
        return (Mutiny.SessionFactory) Proxy.newProxyInstance(
                PersistenceAdapterObservabilityTest.class.getClassLoader(),
                new Class<?>[] { Mutiny.SessionFactory.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("withTransaction")) {
                        return outcome;
                    }
                    if (method.getName().equals("toString")) {
                        return "observability-session-factory";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static NaturalPersonRegistrationCandidate naturalCandidate() {
        NaturalPerson party = naturalPerson();
        PartyIdentifier identifier = identifier(party.partyId());
        return new NaturalPersonRegistrationCandidate(
                METADATA,
                "natural-key",
                "a".repeat(64),
                party,
                SCHEME,
                identifier,
                List.of());
    }

    private static LegalEntityRegistrationCandidate legalCandidate() {
        PartyId partyId = new PartyId(UUID.randomUUID());
        LegalEntity party = LegalEntity.create(
                partyId,
                TENANT_ID,
                null,
                new LegalEntityDetails(
                        "Analytical Engines Ltd",
                        null,
                        "LTD",
                        "GB",
                        LocalDate.of(1843, Month.JANUARY, 1),
                        null),
                TODAY,
                NOW,
                METADATA.userId());
        return new LegalEntityRegistrationCandidate(
                METADATA,
                "legal-key",
                "b".repeat(64),
                party,
                SCHEME,
                identifier(partyId),
                List.of());
    }

    private static HibernateReactivePartyIdentifierMutationAdapter identifierAdapter(Mutiny.SessionFactory factory, RecordingOperationObserver observer) {
        var reader = new PartyIdentifierMutationReader(new PartyIdentifierPersistenceMapper(), new IdentifierSchemePersistenceMapper());
        var effects = new PartyIdentifierMutationEffects(new PartyIdentifierReplayPersistence(new PartyIdentifierCompletionCodec()),
                new PartyIdentifierAuditPersistence(), new PartyIdentifierOutboxPersistence(new PartyOutboxEventPersistenceMapper(), "disabled"));
        var budgets = new PartyMutationTimeouts(new Budgets(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3)));
        return new HibernateReactivePartyIdentifierMutationAdapter(factory, reader, new PartyIdentifierMutationPersistence(new PartyIdentifierPersistenceMapper(), reader),
                new PartyIdentifierMutationLocks(), effects, budgets, observer);
    }

    private static Mutiny.SessionFactory committedFactory(Uni<?> outcome) {
        var session = Mutiny.Session.class.cast(Proxy.newProxyInstance(Mutiny.Session.class.getClassLoader(), new Class<?>[]{Mutiny.Session.class},
                (_, method, _) -> switch (method.getName()) {
                    case "withTransaction" -> outcome;
                    case "close" -> Uni.createFrom().voidItem();
                    default -> throw new AssertionError("Unexpected committed session operation");
                }));
        return Mutiny.SessionFactory.class.cast(Proxy.newProxyInstance(Mutiny.SessionFactory.class.getClassLoader(), new Class<?>[]{Mutiny.SessionFactory.class},
                (_, method, _) -> {
                    if (method.getName().equals("openSession")) { return Uni.createFrom().item(session); }
                    throw new AssertionError("Unexpected committed factory operation");
                }));
    }

    /** Provides finite configuration for the terminal-result observation double. */
    private record Budgets(Duration lockTimeout, Duration statementTimeout, Duration operationTimeout) implements PartyMutationConfiguration { }

    private static NaturalPerson naturalPerson() {
        return NaturalPerson.create(
                new PartyId(UUID.fromString("01991ebd-6800-7000-8000-000000000003")),
                TENANT_ID,
                null,
                new NaturalPersonDetails(
                        "Ada",
                        "Lovelace",
                        null,
                        LocalDate.of(1815, Month.DECEMBER, 10),
                        null,
                        "GB"),
                TODAY,
                NOW,
                METADATA.userId());
    }

    private static PartyIdentifier identifier(PartyId partyId) {
        return PartyIdentifier.builder()
                .identifierId(new PartyIdentifierId(UUID.randomUUID()))
                .tenantId(TENANT_ID)
                .partyId(partyId)
                .identifierSchemeId(SCHEME.id())
                .protectedValue(new ProtectedIdentifierValue(
                        "v1.protected",
                        1,
                        "c".repeat(64),
                        "**1234",
                        new IdentifierRuleVersion(1)))
                .expiresOn(TODAY.plusYears(1))
                .created(NOW, METADATA.userId())
                .build();
    }

    private static IdentifierScheme scheme() {
        return new IdentifierScheme(
                new IdentifierSchemeId(
                        UUID.fromString("01991ebd-6800-7000-8000-000000000004")),
                "TEST-SCHEME",
                "GB",
                IdentifierCategory.NATIONAL_ID,
                IdentifierSubjectType.BOTH,
                "Test scheme",
                null,
                "TRIM_UPPERCASE_V1",
                "ALPHANUMERIC_V1",
                4,
                16,
                false,
                IdentifierSchemeStatus.ACTIVE,
                IdentifierSchemeVersion.initial(),
                AuditInfo.initial(NOW, "catalog"));
    }

    private static PartyRegistrationResult registrationResult(
            NaturalPersonRegistrationCandidate candidate,
            PartyRegistrationOutcome outcome) {
        return new PartyRegistrationResult(
                PartyDetailsResult.fromAggregate(candidate.party()),
                PartyIdentifierResult.fromAggregate(
                        candidate.initialIdentifier(),
                        candidate.identifierScheme()),
                outcome);
    }

    private static PartyRegistrationResult registrationResult(
            LegalEntityRegistrationCandidate candidate,
            PartyRegistrationOutcome outcome) {
        return new PartyRegistrationResult(
                PartyDetailsResult.fromAggregate(candidate.party()),
                PartyIdentifierResult.fromAggregate(
                        candidate.initialIdentifier(),
                        candidate.identifierScheme()),
                outcome);
    }

    private static void assertObservation(
            RecordingOperationObserver observer,
            ObservedOperation operation,
            OperationOutcome outcome) {
        assertEquals(
                List.of(new RecordingOperationObserver.StartedCall(METADATA, operation)),
                observer.startedCalls());
        assertEquals(
                List.of(new RecordingOperationObserver.CompletedCall(METADATA, operation, outcome)),
                observer.completedCalls());
    }

    private static <T> T awaitItem(Uni<T> operation) {
        UniAssertSubscriber<T> subscriber = operation.subscribe()
                .withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitItem(TIMEOUT).assertCompleted();
        return subscriber.getItem();
    }

    private static Throwable awaitFailure(Uni<?> operation) {
        UniAssertSubscriber<?> subscriber = operation.subscribe()
                .withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitFailure(TIMEOUT).assertFailed();
        return subscriber.getFailure();
    }

    /**
     * Supplies constant-time comparison behavior needed by idempotency persistence
     * construction.
     */
    private static final class FingerprintDouble implements RegistrationFingerprintPort {

        @Override
        public String fingerprint(
                com.alexastudillo.partyregistry.application.command.PartyRegistrationCommand command) {
            return "d".repeat(64);
        }

        @Override
        public boolean matches(String expectedFingerprint, String actualFingerprint) {
            return expectedFingerprint.equals(actualFingerprint);
        }
    }
}
