package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies serial registration write ordering without requiring a database.
 */
class PartyRegistrationWriteSequenceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final PartyRegistrationWriteSequence sequence = new PartyRegistrationWriteSequence();

    @Test
    void executesPartyIdempotencyIdentifierAndOutboxStagesInOrder() {
        List<String> calls = new ArrayList<>();

        sequence.execute(
                successfulStage(calls, "persist-party-details"),
                successfulStage(calls, "flush-party-details"),
                successfulStage(calls, "persist-idempotency"),
                successfulStage(calls, "flush-idempotency"),
                successfulStage(calls, "persist-identifier"),
                List.of(
                        successfulStage(calls, "persist-party-event"),
                        successfulStage(calls, "persist-identifier-event")),
                successfulStage(calls, "final-flush"))
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(TIMEOUT)
                .assertCompleted();

        assertEquals(List.of(
                "persist-party-details",
                "flush-party-details",
                "persist-idempotency",
                "flush-idempotency",
                "persist-identifier",
                "persist-party-event",
                "persist-identifier-event",
                "final-flush"), calls);
    }

    @Test
    void stopsBeforeLaterWritesWhenAnyStageFails() {
        List<String> stageNames = List.of(
                "persist-party-details",
                "flush-party-details",
                "persist-idempotency",
                "flush-idempotency",
                "persist-identifier",
                "persist-party-event",
                "persist-identifier-event",
                "final-flush");

        for (int failureStage = 0; failureStage < stageNames.size(); failureStage++) {
            List<String> calls = new ArrayList<>();
            IllegalStateException failure = new IllegalStateException("injected write failure");

            sequence.execute(
                    selectableStage(calls, stageNames, 0, failureStage, failure),
                    selectableStage(calls, stageNames, 1, failureStage, failure),
                    selectableStage(calls, stageNames, 2, failureStage, failure),
                    selectableStage(calls, stageNames, 3, failureStage, failure),
                    selectableStage(calls, stageNames, 4, failureStage, failure),
                    List.of(
                            selectableStage(calls, stageNames, 5, failureStage, failure),
                            selectableStage(calls, stageNames, 6, failureStage, failure)),
                    selectableStage(calls, stageNames, 7, failureStage, failure))
                    .subscribe().withSubscriber(UniAssertSubscriber.create())
                    .awaitFailure(TIMEOUT)
                    .assertFailedWith(IllegalStateException.class, "injected write failure");

            assertEquals(stageNames.subList(0, failureStage + 1), calls);
        }
    }

    private static Supplier<Uni<?>> successfulStage(List<String> calls, String name) {
        return () -> {
            calls.add(name);
            return Uni.createFrom().voidItem();
        };
    }

    private static Supplier<Uni<?>> selectableStage(
            List<String> calls,
            List<String> stageNames,
            int stage,
            int failureStage,
            RuntimeException failure) {
        return () -> {
            calls.add(stageNames.get(stage));
            return stage == failureStage
                    ? Uni.createFrom().failure(failure)
                    : Uni.createFrom().voidItem();
        };
    }
}
