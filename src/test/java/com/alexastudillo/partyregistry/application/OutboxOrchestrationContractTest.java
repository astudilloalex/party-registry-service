package com.alexastudillo.partyregistry.application;

import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.APPLICATION;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.enumValue;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.invoke;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.sequencedPort;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.useCase;
import static com.alexastudillo.partyregistry.application.ApplicationContractSupport.value;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OutboxOrchestrationContractTest {
    private static final UUID EVENT = UUID.fromString("038f0c72-4a7b-7c91-8b2a-1234567890ab");
    private static final UUID TENANT = UUID.fromString("038f0c72-4a7b-7c91-8b2a-1234567890ac");
    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

    @ParameterizedTest(name = "broker {0} records {1}")
    @MethodSource("publicationOutcomes")
    void claimCommitsBeforeBrokerIoAndOutcomeUsesEventIdAndClaimedVersion(
            String publisherOutcome, String recordedOutcome) {
        List<String> sequence = new ArrayList<>();
        var store = sequencedPort("OutboxStorePort", sequence)
                .answers("claimEligible", arguments -> {
                    assertEquals(10, arguments[0]);
                    assertEquals(NOW, arguments[1]);
                    return CompletableFuture.completedFuture(List.of(claim()));
                })
                .answers("recordOutcome", arguments -> {
                    assertEquals(EVENT, arguments[0]);
                    assertEquals(4L, arguments[1]);
                    assertEquals(recordedOutcome, String.valueOf(arguments[2]));
                    return CompletableFuture.completedFuture(true);
                });
        var publisher = sequencedPort("IntegrationEventPublisherPort", sequence)
                .answers("publish", arguments -> {
                    assertEquals(EVENT, invoke(arguments[0], "eventId"));
                    return CompletableFuture.completedFuture(enumValue("PublicationOutcome", publisherOutcome));
                });
        Object service = useCase("PublishOutboxBatchUseCase", store.proxy(), publisher.proxy());

        Object summary = invoke(service, "execute", 10, NOW);

        assertEquals(List.of(
                "OutboxStorePort.claimEligible",
                "IntegrationEventPublisherPort.publish",
                "OutboxStorePort.recordOutcome"), sequence);
        assertEquals(1, invoke(summary, "claimed"));
        assertEquals(1, invoke(summary, "processed"));
    }

    @Test
    void emptyClaimPerformsNoBrokerOrOutcomeOperation() {
        List<String> sequence = new ArrayList<>();
        var store = sequencedPort("OutboxStorePort", sequence)
                .returns("claimEligible", CompletableFuture.completedFuture(List.of()))
                .returns("recordOutcome", CompletableFuture.completedFuture(true));
        var publisher = sequencedPort("IntegrationEventPublisherPort", sequence)
                .returns("publish", CompletableFuture.completedFuture(enumValue("PublicationOutcome", "CONFIRMED")));
        Object service = useCase("PublishOutboxBatchUseCase", store.proxy(), publisher.proxy());

        Object summary = invoke(service, "execute", 10, NOW);

        assertEquals(List.of("OutboxStorePort.claimEligible"), sequence);
        assertEquals(0, invoke(summary, "processed"));
    }

    @Test
    void authorisedRecoveryRequeuesSameFailedEventWithoutReplacementIdentity() {
        List<String> sequence = new ArrayList<>();
        var store = sequencedPort("OutboxStorePort", sequence)
                .answers("recoverFailed", arguments -> {
                    assertEquals(EVENT, arguments[0]);
                    assertEquals(6L, arguments[1]);
                    return CompletableFuture.completedFuture(value(
                            "RecoveredOutboxEvent", EVENT, 7L, "party.updated.v1", "{\"synthetic\":true}"));
                });
        Object service = useCase("RecoverFailedOutboxEventUseCase", store.proxy());

        Object recovered = invoke(service, "execute", EVENT, 6L);

        assertEquals(List.of("OutboxStorePort.recoverFailed"), sequence);
        assertEquals(EVENT, invoke(recovered, "eventId"));
        assertEquals("{\"synthetic\":true}", invoke(recovered, "payload"));
    }

    @Test
    void staleOutcomeIsRejectedAndCannotOverwriteLaterClaimState() {
        List<String> sequence = new ArrayList<>();
        var store = sequencedPort("OutboxStorePort", sequence)
                .returns("claimEligible", CompletableFuture.completedFuture(List.of(claim())))
                .returns("recordOutcome", CompletableFuture.completedFuture(false));
        var publisher = sequencedPort("IntegrationEventPublisherPort", sequence)
                .returns("publish", CompletableFuture.completedFuture(enumValue("PublicationOutcome", "CONFIRMED")));
        Object service = useCase("PublishOutboxBatchUseCase", store.proxy(), publisher.proxy());

        Object summary = invoke(service, "execute", 10, NOW);

        assertEquals(List.of(
                "OutboxStorePort.claimEligible",
                "IntegrationEventPublisherPort.publish",
                "OutboxStorePort.recordOutcome"), sequence);
        assertEquals(1, invoke(summary, "staleOutcomes"));
        assertEquals(1, store.count("recordOutcome"));
    }

    private static Stream<Arguments> publicationOutcomes() {
        return Stream.of(
                Arguments.of("CONFIRMED", "PUBLISHED"),
                Arguments.of("TRANSIENT_FAILURE", "PENDING"),
                Arguments.of("UNKNOWN", "PENDING"),
                Arguments.of("NON_RECOVERABLE_FAILURE", "FAILED"));
    }

    private static Object claim() {
        return value("OutboxClaim", EVENT, TENANT, 4L, "party.updated.v1", 1,
                "{\"synthetic\":true}", NOW);
    }
}
