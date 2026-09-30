package com.alexastudillo.partyregistry.infrastructure.messaging;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies RabbitMQ metadata and that sender completion follows connector acknowledgement.
 */
class RabbitMqOutboxMessageSenderTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @Test
    void returnsTheEmitterAcknowledgementAndSetsPersistentSafeMetadata() {
        CompletableFuture<Void> acknowledgement = new CompletableFuture<>();
        AtomicReference<Message<byte[]>> captured = new AtomicReference<>();
        RabbitMqOutboxMessageSender sender = new RabbitMqOutboxMessageSender(message -> {
            captured.set(message);
            return Uni.createFrom().completionStage(acknowledgement);
        });
        OutboxMessage message = message();
        String json = "{\"eventId\":\"" + message.eventId() + "\"}";

        UniAssertSubscriber<Void> subscriber = sender.send(message, json)
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        subscriber.assertNotTerminated();
        Message<byte[]> outgoing = captured.get();
        assertNotNull(outgoing);
        assertEquals(json, new String(outgoing.getPayload(), StandardCharsets.UTF_8));
        OutgoingRabbitMQMetadata metadata = outgoing
                .getMetadata(OutgoingRabbitMQMetadata.class)
                .orElseThrow();
        assertEquals(message.eventId().toString(), metadata.getMessageId());
        assertEquals("application/json", metadata.getContentType());
        assertEquals(2, metadata.getDeliveryMode());
        assertEquals(message.eventType(), metadata.getType());
        assertEquals(message.correlationId(), metadata.getCorrelationId());

        acknowledgement.complete(null);
        subscriber.awaitItem(TIMEOUT).assertCompleted();
    }

    private static OutboxMessage message() {
        return new OutboxMessage(
                UUID.fromString("01991bc2-8c00-7000-8000-000000000001"),
                UUID.fromString("01991bc2-8c00-7000-8000-000000000002"),
                "PARTY",
                UUID.fromString("01991bc2-8c00-7000-8000-000000000003"),
                0,
                "party.created.v1",
                (short) 1,
                Map.of("partyType", "NATURAL_PERSON"),
                Instant.parse("2026-09-04T14:00:00Z"),
                "01991bc2-8c00-7000-8000-000000000004",
                null);
    }

}
