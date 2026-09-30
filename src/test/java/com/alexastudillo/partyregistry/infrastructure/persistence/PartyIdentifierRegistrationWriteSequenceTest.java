package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies the additional-identifier write sequence without a database.
 */
class PartyIdentifierRegistrationWriteSequenceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final PartyIdentifierRegistrationWriteSequence sequence =
            new PartyIdentifierRegistrationWriteSequence();

    @Test
    void exposesNoPartyOrDetailWriteStage() throws NoSuchMethodException {
        Method execute = PartyIdentifierRegistrationWriteSequence.class.getDeclaredMethod(
                "execute",
                Supplier.class,
                List.class,
                Supplier.class);

        String signature = execute.toGenericString();
        assertFalse(signature.contains("PartyEntity"));
        assertFalse(signature.contains("NaturalPersonDetailsEntity"));
        assertFalse(signature.contains("LegalEntityDetailsEntity"));
    }

    @Test
    void executesIdentifierThenOutboxWritesSeriallyBeforeFlush() {
        List<String> calls = new ArrayList<>();

        sequence.execute(
                successfulStage(calls, "persist-identifier"),
                List.of(
                        successfulStage(calls, "persist-identifier-event-1"),
                        successfulStage(calls, "persist-identifier-event-2")),
                successfulStage(calls, "flush"))
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(TIMEOUT)
                .assertCompleted();

        assertEquals(List.of(
                "persist-identifier",
                "persist-identifier-event-1",
                "persist-identifier-event-2",
                "flush"), calls);
    }

    @Test
    void stopsBeforeLaterWritesWhenAStageFails() {
        List<String> stageNames = List.of(
                "persist-identifier",
                "persist-identifier-event-1",
                "persist-identifier-event-2",
                "flush");

        for (int failureStage = 0; failureStage < stageNames.size(); failureStage++) {
            List<String> calls = new ArrayList<>();
            IllegalStateException failure = new IllegalStateException("injected write failure");

            sequence.execute(
                    selectableStage(calls, stageNames, 0, failureStage, failure),
                    List.of(
                            selectableStage(calls, stageNames, 1, failureStage, failure),
                            selectableStage(calls, stageNames, 2, failureStage, failure)),
                    selectableStage(calls, stageNames, 3, failureStage, failure))
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
