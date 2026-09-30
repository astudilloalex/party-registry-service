package com.alexastudillo.partyregistry.infrastructure.messaging;

import io.smallrye.mutiny.Uni;

/**
 * Sends one serialized safe outbox message and completes only after broker acknowledgement.
 */
public interface OutboxMessageSender {

    Uni<Void> send(OutboxMessage message, String serializedMessage);
}
