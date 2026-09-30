package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Persists one independent identifier and its outbox rows in strict serial order.
 */
final class PartyIdentifierRegistrationWriteSequence {

    Uni<Void> execute(
            Supplier<Uni<?>> persistIdentifier,
            List<Supplier<Uni<?>>> persistOutboxEvents,
            Supplier<Uni<?>> flush) {
        Objects.requireNonNull(persistIdentifier, "persistIdentifier");
        Objects.requireNonNull(persistOutboxEvents, "persistOutboxEvents");
        Objects.requireNonNull(flush, "flush");

        Uni<Void> sequence = Uni.createFrom().voidItem().call(persistIdentifier);
        for (Supplier<Uni<?>> persistOutboxEvent : persistOutboxEvents) {
            sequence = sequence.call(Objects.requireNonNull(persistOutboxEvent, "persistOutboxEvent"));
        }
        return sequence.call(flush);
    }
}
