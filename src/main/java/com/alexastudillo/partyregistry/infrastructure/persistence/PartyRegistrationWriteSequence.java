package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Executes registration writes serially in the order required for race arbitration.
 */
final class PartyRegistrationWriteSequence {

    Uni<Void> execute(
            Supplier<Uni<?>> persistPartyAndDetails,
            Supplier<Uni<?>> flushPartyAndDetails,
            Supplier<Uni<?>> persistIdempotencyRecord,
            Supplier<Uni<?>> flushIdempotencyRecord,
            Supplier<Uni<?>> persistIdentifier,
            List<Supplier<Uni<?>>> persistOutboxEvents,
            Supplier<Uni<?>> finalFlush) {
        Objects.requireNonNull(persistPartyAndDetails, "persistPartyAndDetails");
        Objects.requireNonNull(flushPartyAndDetails, "flushPartyAndDetails");
        Objects.requireNonNull(persistIdempotencyRecord, "persistIdempotencyRecord");
        Objects.requireNonNull(flushIdempotencyRecord, "flushIdempotencyRecord");
        Objects.requireNonNull(persistIdentifier, "persistIdentifier");
        Objects.requireNonNull(persistOutboxEvents, "persistOutboxEvents");
        Objects.requireNonNull(finalFlush, "finalFlush");

        Uni<Void> sequence = Uni.createFrom().voidItem()
                .call(persistPartyAndDetails)
                .call(flushPartyAndDetails)
                .call(persistIdempotencyRecord)
                .call(flushIdempotencyRecord)
                .call(persistIdentifier);
        for (Supplier<Uni<?>> persistOutboxEvent : persistOutboxEvents) {
            sequence = sequence.call(Objects.requireNonNull(persistOutboxEvent, "persistOutboxEvent"));
        }
        return sequence.call(finalFlush);
    }
}
