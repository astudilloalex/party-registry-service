package com.alexastudillo.partyregistry.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Validates the approved event shapes and serializes only their safe broker envelope.
 */
@ApplicationScoped
public class OutboxMessageSerializer {

    private static final String PARTY_CREATED = "party.created.v1";
    private static final String IDENTIFIER_CREATED = "party.identifier-created.v1";
    private static final String PARTY_ACTIVATED = "party.activated.v1";
    private static final Set<String> PARTY_TYPES = Set.of("NATURAL_PERSON", "LEGAL_ENTITY");

    private static final String PAYLOAD_KEY_PARTY_TYPE = "partyType";
    private static final String PAYLOAD_KEY_STATUS = "status";
    private static final String PAYLOAD_KEY_PARTY_ID = "partyId";
    private static final String PAYLOAD_KEY_SCHEME_CODE = "schemeCode";

    private final ObjectMapper objectMapper;

    /**
     * Creates the serializer with the application JSON mapper.
     */
    @Inject
    public OutboxMessageSerializer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public String serialize(OutboxMessage message) {
        Objects.requireNonNull(message, "message");
        requireApprovedShape(message);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", message.eventId());
        envelope.put("tenantId", message.tenantId());
        envelope.put("aggregateType", message.aggregateType());
        envelope.put("aggregateId", message.aggregateId());
        envelope.put("aggregateVersion", message.aggregateVersion());
        envelope.put("eventType", message.eventType());
        envelope.put("eventSchemaVersion", message.eventSchemaVersion());
        envelope.put("payload", new TreeMap<>(message.payload()));
        envelope.put("occurredAt", message.occurredAt());
        envelope.put("correlationId", message.correlationId());
        envelope.put("causationId", message.causationId());
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Outbox message serialization failed", exception);
        }
    }

    private static void requireApprovedShape(OutboxMessage message) {
        if (message.eventSchemaVersion() != 1
                || message.correlationId() == null
                || message.correlationId().isBlank()
                || message.causationId() != null && message.causationId().isBlank()) {
            throw new IllegalArgumentException("Outbox event envelope is inconsistent");
        }
        switch (message.eventType()) {
            case PARTY_CREATED -> requirePartyCreated(message);
            case IDENTIFIER_CREATED -> requireIdentifierCreated(message);
            case PARTY_ACTIVATED -> requirePartyActivated(message);
            default -> throw new IllegalArgumentException("Unsupported outbox event type");
        }
    }

    private static void requirePartyCreated(OutboxMessage message) {
        requireAggregate(message, "PARTY", 0, true);
        Map<String, String> payload = requirePayload(message, Set.of(PAYLOAD_KEY_PARTY_TYPE));
        if (!PARTY_TYPES.contains(payload.get(PAYLOAD_KEY_PARTY_TYPE))) {
            throw new IllegalArgumentException("Party-created payload is inconsistent");
        }
    }

    private static void requireIdentifierCreated(OutboxMessage message) {
        requireAggregate(message, "PARTY_IDENTIFIER", 0, true);
        Map<String, String> payload = requirePayload(
                message,
                Set.of(PAYLOAD_KEY_PARTY_ID, PAYLOAD_KEY_SCHEME_CODE, PAYLOAD_KEY_STATUS));
        if (!"PENDING_VERIFICATION".equals(payload.get(PAYLOAD_KEY_STATUS))) {
            throw new IllegalArgumentException("Identifier-created payload is inconsistent");
        }
        UUID.fromString(payload.get(PAYLOAD_KEY_PARTY_ID));
    }

    private static void requirePartyActivated(OutboxMessage message) {
        requireAggregate(message, "PARTY", 0, false);
        Map<String, String> payload = requirePayload(message, Set.of(PAYLOAD_KEY_PARTY_TYPE, PAYLOAD_KEY_STATUS));
        if (!PARTY_TYPES.contains(payload.get(PAYLOAD_KEY_PARTY_TYPE))
                || !"ACTIVE".equals(payload.get(PAYLOAD_KEY_STATUS))) {
            throw new IllegalArgumentException("Party-activated payload is inconsistent");
        }
    }

    private static void requireAggregate(
            OutboxMessage message,
            String aggregateType,
            long boundaryVersion,
            boolean exactVersion) {
        if (!aggregateType.equals(message.aggregateType())
                || exactVersion && message.aggregateVersion() != boundaryVersion
                || !exactVersion && message.aggregateVersion() <= boundaryVersion) {
            throw new IllegalArgumentException("Outbox aggregate metadata is inconsistent");
        }
    }

    private static Map<String, String> requirePayload(
            OutboxMessage message,
            Set<String> expectedKeys) {
        Map<String, String> payload = message.payload();
        if (!payload.keySet().equals(expectedKeys)
                || payload.values().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Outbox payload shape is inconsistent");
        }
        return payload;
    }
}
