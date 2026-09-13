package com.alexastudillo.partyregistry.infrastructure.messaging;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Narrows the connector emitter to its acknowledgement-aware send operation.
 */
@FunctionalInterface
interface AcknowledgedRabbitMqEmitter {

    Uni<Void> send(Message<byte[]> message);
}
