package com.alexastudillo.partyregistry.infrastructure.messaging;

import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Sends persistent JSON messages through the confirmed RabbitMQ outgoing channel.
 */
@ApplicationScoped
public class RabbitMqOutboxMessageSender implements OutboxMessageSender {

    private final AcknowledgedRabbitMqEmitter emitter;

    /**
     * Creates the sender from the connector-managed acknowledged emitter.
     */
    @Inject
    public RabbitMqOutboxMessageSender(
            @Channel("party-outbox") MutinyEmitter<byte[]> emitter) {
        Objects.requireNonNull(emitter, "emitter");
        this.emitter = emitter::sendMessage;
    }

    RabbitMqOutboxMessageSender(AcknowledgedRabbitMqEmitter emitter) {
        this.emitter = Objects.requireNonNull(emitter, "emitter");
    }

    @Override
    public Uni<Void> send(OutboxMessage message, String serializedMessage) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(serializedMessage, "serializedMessage");
        OutgoingRabbitMQMetadata.Builder metadata = OutgoingRabbitMQMetadata.builder()
                .withMessageId(message.eventId().toString())
                .withContentType("application/json")
                .withDeliveryMode(2)
                .withType(message.eventType())
                .withTimestamp(message.occurredAt().atZone(ZoneOffset.UTC));
        if (message.correlationId() != null) {
            metadata.withCorrelationId(message.correlationId());
        }
        Message<byte[]> outgoing = Message.of(
                serializedMessage.getBytes(StandardCharsets.UTF_8),
                Metadata.of(metadata.build()));
        return emitter.send(outgoing);
    }
}
