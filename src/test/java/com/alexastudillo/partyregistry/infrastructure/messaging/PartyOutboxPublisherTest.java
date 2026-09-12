package com.alexastudillo.partyregistry.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies bounded at-least-once publication behavior with narrow reactive fakes.
 */
class PartyOutboxPublisherTest {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(2);
    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("01991b8d-6c00-7000-8000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("01991b8d-6c00-7000-8000-000000000002");
    private static final UUID PARTY_ID = UUID.fromString("01991b8d-6c00-7000-8000-000000000003");
    private static final String CORRELATION_ID = "01991b8d-6c00-7000-8000-000000000004";

    @Test
    void disabledAndStoredOnlyModesNeitherClaimNorSend() {
        for (String mode : List.of("disabled", "stored-only")) {
            FakeStore store = new FakeStore(List.of(List.of(claimed(partyCreated(), 1))));
            FakeSender sender = FakeSender.succeeding();

            awaitSuccess(publisher(store, sender, configuration(), mode).publishDueEvents());

            assertEquals(0, store.claimCalls.get());
            assertTrue(sender.messages.isEmpty());
            assertTrue(store.transitions.isEmpty());
        }
    }

    @Test
    void claimsSendsAndMarksPublishedOnlyAfterAcknowledgement() {
        FakeStore store = new FakeStore(List.of(List.of(claimed(partyCreated(), 1))));
        FakeSender sender = FakeSender.succeeding();

        awaitSuccess(publisher(store, sender, configuration(), "published").publishDueEvents());

        assertEquals(1, store.claimCalls.get());
        assertEquals(1, sender.messages.size());
        assertEquals(EVENT_ID, sender.messages.getFirst().eventId());
        assertEquals(List.of(new Transition(EVENT_ID, 1, "published", NOW, null)), store.transitions);
    }

    @Test
    void retryableFailureKeepsPendingWithFiniteExponentialBackoff() {
        FakeStore store = new FakeStore(List.of(List.of(claimed(partyCreated(), 3))));
        FakeSender sender = new FakeSender(message -> Uni.createFrom().failure(
                new IllegalStateException("broker diagnostic that must not persist")));

        awaitSuccess(publisher(store, sender, configuration(), "published").publishDueEvents());

        assertEquals(List.of(new Transition(
                EVENT_ID,
                3,
                "broker-unavailable",
                NOW,
                NOW.plusSeconds(4))), store.transitions);
        assertFalse(store.transitions.toString().contains("diagnostic"));
    }

    @Test
    void maximumAttemptMarksTerminalFailure() {
        PublisherConfiguration maximumThreeAttempts = configuration(
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ofMinutes(5),
                3);
        FakeStore store = new FakeStore(List.of(List.of(claimed(partyCreated(), 3))));
        FakeSender sender = new FakeSender(message -> Uni.createFrom().failure(
                new IllegalStateException("unavailable")));

        awaitSuccess(publisher(store, sender, maximumThreeAttempts, "published")
                .publishDueEvents());

        assertEquals(List.of(new Transition(
                EVENT_ID,
                3,
                "failed:broker-unavailable",
                NOW,
                null)), store.transitions);
    }

    @Test
    void oneOrdinaryEventFailureDoesNotPreventTheNextClaimedEvent() {
        OutboxMessage first = partyCreated();
        OutboxMessage second = new OutboxMessage(
                UUID.fromString("01991b8d-6c00-7000-8000-000000000005"),
                TENANT_ID,
                "PARTY",
                UUID.fromString("01991b8d-6c00-7000-8000-000000000006"),
                0,
                "party.created.v1",
                (short) 1,
                Map.of("partyType", "LEGAL_ENTITY"),
                NOW,
                CORRELATION_ID,
                null);
        FakeStore store = new FakeStore(List.of(List.of(claimed(first, 1), claimed(second, 1))));
        FakeSender sender = new FakeSender(message -> message.eventId().equals(EVENT_ID)
                ? Uni.createFrom().failure(new IllegalStateException("unavailable"))
                : Uni.createFrom().voidItem());

        awaitSuccess(publisher(store, sender, configuration(), "published").publishDueEvents());

        assertEquals(List.of(first, second), sender.messages);
        assertTrue(store.transitions.stream().anyMatch(transition ->
                transition.eventId().equals(first.eventId())
                        && transition.outcome().equals("broker-unavailable")));
        assertTrue(store.transitions.stream().anyMatch(transition ->
                transition.eventId().equals(second.eventId())
                        && transition.outcome().equals("published")));
    }

    @Test
    void duplicateRetriesKeepTheExactEventIdentityAndSerializedMessage() {
        OutboxMessage message = partyCreated();
        FakeStore store = new FakeStore(List.of(
                List.of(claimed(message, 1)),
                List.of(claimed(message, 2))));
        FakeSender sender = FakeSender.succeeding();
        PartyOutboxPublisher publisher = publisher(store, sender, configuration(), "published");

        awaitSuccess(publisher.publishDueEvents());
        awaitSuccess(publisher.publishDueEvents());

        assertEquals(List.of(message, message), sender.messages);
        assertEquals(2, sender.serializedMessages.size());
        assertEquals(sender.serializedMessages.get(0), sender.serializedMessages.get(1));
        assertTrue(sender.serializedMessages.getFirst().contains(EVENT_ID.toString()));
    }

    @Test
    void classifiesARealFinitePipelineTimeoutForRetry() {
        PublisherConfiguration shortTimeout = configuration(
                Duration.ofMillis(25),
                Duration.ofSeconds(1),
                Duration.ofMillis(10),
                Duration.ofSeconds(1),
                3);
        FakeStore store = new FakeStore(List.of(List.of(claimed(partyCreated(), 1))));
        FakeSender sender = new FakeSender(message -> Uni.createFrom().nothing());

        awaitSuccess(publisher(store, sender, shortTimeout, "published").publishDueEvents());

        assertEquals(List.of(new Transition(
                EVENT_ID,
                1,
                "broker-timeout",
                NOW,
                NOW.plusMillis(10))), store.transitions);
    }

    @Test
    void cancellationIsPropagatedWithoutChangingTheClaimedRow() {
        CancellationException cancellation = new CancellationException("cancel current poll");
        FakeStore store = new FakeStore(List.of(List.of(claimed(partyCreated(), 1))));
        FakeSender sender = new FakeSender(message -> Uni.createFrom().failure(cancellation));

        UniAssertSubscriber<Void> subscriber = publisher(store, sender, configuration(), "published")
                .publishDueEvents()
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitFailure(
                failure -> assertSame(cancellation, failure),
                TEST_TIMEOUT)
                .assertFailed();

        assertTrue(store.transitions.isEmpty());
    }

    @Test
    void invalidOrConfidentialPayloadIsTerminalAndNeverSent() {
        OutboxMessage invalid = new OutboxMessage(
                EVENT_ID,
                TENANT_ID,
                "PARTY_IDENTIFIER",
                PARTY_ID,
                0,
                "party.identifier-created.v1",
                (short) 1,
                Map.of(
                        "partyId", PARTY_ID.toString(),
                        "schemeCode", "GB-PASSPORT",
                        "status", "PENDING_VERIFICATION",
                        "encryptedValue", "ciphertext",
                        "maskedValue", "****1234"),
                NOW,
                CORRELATION_ID,
                null);
        FakeStore store = new FakeStore(List.of(List.of(claimed(invalid, 1))));
        FakeSender sender = FakeSender.succeeding();

        awaitSuccess(publisher(store, sender, configuration(), "published").publishDueEvents());

        assertTrue(sender.messages.isEmpty());
        assertEquals(List.of(new Transition(
                EVENT_ID,
                1,
                "failed:serialization-failed",
                NOW,
                null)), store.transitions);
        assertFalse(store.transitions.toString().contains("ciphertext"));
        assertFalse(store.transitions.toString().contains("1234"));
    }

    @Test
    void rejectsEveryUnboundedOrNonpositiveConfiguration() {
        List<PublisherConfiguration> invalid = new ArrayList<>();
        invalid.add(new PublisherConfiguration(Duration.ZERO, 25, Duration.ofSeconds(5),
                Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofMinutes(5), 10));
        invalid.add(new PublisherConfiguration(Duration.ofHours(2), 25, Duration.ofSeconds(5),
                Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofMinutes(5), 10));
        invalid.add(new PublisherConfiguration(Duration.ofSeconds(5), 0, Duration.ofSeconds(5),
                Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofMinutes(5), 10));
        invalid.add(new PublisherConfiguration(Duration.ofSeconds(5), 501, Duration.ofSeconds(5),
                Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofMinutes(5), 10));
        invalid.add(configuration(Duration.ZERO, Duration.ofSeconds(30), Duration.ofSeconds(1),
                Duration.ofMinutes(5), 10));
        invalid.add(configuration(Duration.ofMinutes(6), Duration.ofMinutes(7), Duration.ofSeconds(1),
                Duration.ofMinutes(5), 10));
        invalid.add(configuration(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(1),
                Duration.ofMinutes(5), 10));
        invalid.add(configuration(Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ZERO,
                Duration.ofMinutes(5), 10));
        invalid.add(configuration(Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(6),
                Duration.ofMinutes(5), 10));
        invalid.add(configuration(Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(1),
                Duration.ofHours(25), 10));
        invalid.add(configuration(Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(1),
                Duration.ofMinutes(5), 0));
        invalid.add(configuration(Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(1),
                Duration.ofMinutes(5), 101));

        for (PublisherConfiguration configuration : invalid) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> publisher(new FakeStore(List.of()), FakeSender.succeeding(), configuration, "published"));
        }
    }

    private static PartyOutboxPublisher publisher(
            FakeStore store,
            FakeSender sender,
            OutboxPublisherConfiguration configuration,
            String mode) {
        return new PartyOutboxPublisher(
                store,
                sender,
                serializer(),
                configuration,
                mode,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OutboxMessageSerializer serializer() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new OutboxMessageSerializer(objectMapper);
    }

    private static PublisherConfiguration configuration() {
        return configuration(
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                Duration.ofMinutes(5),
                10);
    }

    private static PublisherConfiguration configuration(
            Duration publishTimeout,
            Duration claimLease,
            Duration initialBackoff,
            Duration maximumBackoff,
            int maximumAttempts) {
        return new PublisherConfiguration(
                Duration.ofSeconds(5),
                25,
                publishTimeout,
                claimLease,
                initialBackoff,
                maximumBackoff,
                maximumAttempts);
    }

    private static OutboxMessage partyCreated() {
        return new OutboxMessage(
                EVENT_ID,
                TENANT_ID,
                "PARTY",
                PARTY_ID,
                0,
                "party.created.v1",
                (short) 1,
                Map.of("partyType", "NATURAL_PERSON"),
                NOW,
                CORRELATION_ID,
                null);
    }

    private static ClaimedOutboxEvent claimed(OutboxMessage message, int attempt) {
        return new ClaimedOutboxEvent(message, attempt);
    }

    private static void awaitSuccess(Uni<Void> operation) {
        operation.subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(TEST_TIMEOUT)
                .assertCompleted();
    }

    /**
     * Supplies immutable settings directly to the publisher under test.
     */
    private record PublisherConfiguration(
            Duration pollInterval,
            int batchSize,
            Duration publishTimeout,
            Duration claimLease,
            Duration initialBackoff,
            Duration maximumBackoff,
            int maximumAttempts) implements OutboxPublisherConfiguration {
    }

    /**
     * Records claim and transition calls without a database.
     */
    private static final class FakeStore implements OutboxDeliveryStore {

        private final List<List<ClaimedOutboxEvent>> batches;
        private final AtomicInteger claimCalls = new AtomicInteger();
        private final List<Transition> transitions = new CopyOnWriteArrayList<>();

        private FakeStore(List<List<ClaimedOutboxEvent>> batches) {
            this.batches = List.copyOf(batches);
        }

        @Override
        public Uni<List<ClaimedOutboxEvent>> claimDue(
                int batchSize,
                Instant claimedAt,
                Duration lease) {
            int index = claimCalls.getAndIncrement();
            List<ClaimedOutboxEvent> batch = index < batches.size()
                    ? batches.get(index)
                    : List.of();
            return Uni.createFrom().item(batch);
        }

        @Override
        public Uni<Void> markPublished(UUID eventId, int claimedAttempt, Instant acknowledgedAt) {
            transitions.add(new Transition(eventId, claimedAttempt, "published", acknowledgedAt, null));
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Void> scheduleRetry(
                UUID eventId,
                int claimedAttempt,
                Instant failedAt,
                Instant retryAt,
                String errorCode) {
            transitions.add(new Transition(eventId, claimedAttempt, errorCode, failedAt, retryAt));
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Void> markFailed(
                UUID eventId,
                int claimedAttempt,
                Instant failedAt,
                String errorCode) {
            transitions.add(new Transition(
                    eventId,
                    claimedAttempt,
                    "failed:" + errorCode,
                    failedAt,
                    null));
            return Uni.createFrom().voidItem();
        }
    }

    /**
     * Captures safe messages and supplies one configurable acknowledgement outcome.
     */
    private static final class FakeSender implements OutboxMessageSender {

        private final Function<OutboxMessage, Uni<Void>> result;
        private final List<OutboxMessage> messages = new CopyOnWriteArrayList<>();
        private final List<String> serializedMessages = new CopyOnWriteArrayList<>();

        private FakeSender(Function<OutboxMessage, Uni<Void>> result) {
            this.result = result;
        }

        private static FakeSender succeeding() {
            return new FakeSender(message -> Uni.createFrom().voidItem());
        }

        @Override
        public Uni<Void> send(OutboxMessage message, String serializedMessage) {
            messages.add(message);
            serializedMessages.add(serializedMessage);
            return result.apply(message);
        }
    }

    /**
     * Captures one attempt-qualified persistence transition.
     */
    private record Transition(
            UUID eventId,
            int attempt,
            String outcome,
            Instant occurredAt,
            Instant retryAt) {
    }
}
