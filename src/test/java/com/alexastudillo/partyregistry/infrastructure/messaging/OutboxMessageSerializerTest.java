package com.alexastudillo.partyregistry.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies exact version-one event shapes and publisher-envelope confidentiality.
 */
class OutboxMessageSerializerTest {

    private static final UUID EVENT_ID = UUID.fromString("01991bb4-9800-7000-8000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("01991bb4-9800-7000-8000-000000000002");
    private static final UUID AGGREGATE_ID = UUID.fromString("01991bb4-9800-7000-8000-000000000003");
    private static final Instant OCCURRED_AT = LocalDate.of(2026, Month.SEPTEMBER, 4).atTime(13, 0).toInstant(ZoneOffset.UTC);
    private static final String CORRELATION_ID = "01991bb4-9800-7000-8000-000000000004";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final OutboxMessageSerializer serializer = new OutboxMessageSerializer(objectMapper);

    @Test
    void serializesEveryRequiredSafeEnvelopeFieldAndNothingElse() throws Exception {
        OutboxMessage message = message(
                "PARTY_IDENTIFIER",
                0,
                "party.identifier-created.v1",
                Map.of(
                        "partyId", AGGREGATE_ID.toString(),
                        "schemeCode", "GB-PASSPORT",
                        "status", "PENDING_VERIFICATION"));

        String serialized = serializer.serialize(message);
        JsonNode root = objectMapper.readTree(serialized);

        assertEquals(Set.of(
                "eventId",
                "tenantId",
                "aggregateType",
                "aggregateId",
                "aggregateVersion",
                "eventType",
                "eventSchemaVersion",
                "payload",
                "occurredAt",
                "correlationId",
                "causationId"), root.properties().stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
        assertEquals(EVENT_ID.toString(), root.get("eventId").asText());
        assertEquals(TENANT_ID.toString(), root.get("tenantId").asText());
        assertEquals("PARTY_IDENTIFIER", root.get("aggregateType").asText());
        assertEquals("party.identifier-created.v1", root.get("eventType").asText());
        assertEquals(Set.of("partyId", "schemeCode", "status"),
                root.get("payload").properties().stream()
                        .map(Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(root.get("causationId").isNull());
        assertFalse(serialized.contains("createdBy"));
        assertFalse(serialized.contains("publishAttempts"));
        assertFalse(serialized.contains("nextAttemptAt"));
        assertFalse(serialized.contains("lastError"));
        assertFalse(serialized.contains("maskedValue"));
        assertFalse(serialized.contains("encryptedValue"));
        assertFalse(serialized.contains("normalizedValue"));
        assertFalse(serialized.contains("fingerprint"));
        assertFalse(serialized.contains("keyVersion"));
    }

    @Test
    void producesStableJsonForDuplicateDeliveryOfTheSameStoredEvent() {
        OutboxMessage message = message(
                "PARTY",
                0,
                "party.created.v1",
                Map.of("partyType", "NATURAL_PERSON"));

        assertEquals(serializer.serialize(message), serializer.serialize(message));
    }

    @Test
    void preservesExistingApprovedPayloadShapesAndRejectsUnknownEvents() {
        serializer.serialize(message(
                "PARTY",
                0,
                "party.created.v1",
                Map.of("partyType", "LEGAL_ENTITY")));
        serializer.serialize(message(
                "PARTY",
                2,
                "party.activated.v1",
                Map.of("partyType", "NATURAL_PERSON", "status", "ACTIVE")));

        OutboxMessage invalidParty = message(
                "PARTY",
                0,
                "party.created.v1",
                Map.of("partyType", "NATURAL_PERSON", "status", "DRAFT"));
        OutboxMessage invalidIdentifier = message(
                "PARTY_IDENTIFIER",
                0,
                "party.identifier-created.v1",
                Map.of(
                        "partyId", AGGREGATE_ID.toString(),
                        "schemeCode", "GB-PASSPORT",
                        "status", "VERIFIED"));
        OutboxMessage unknownEvent = message(
                "PARTY",
                1,
                "party.unknown.v1",
                Map.of("partyType", "NATURAL_PERSON"));
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(invalidParty));
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(invalidIdentifier));
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(unknownEvent));
    }

    private static OutboxMessage message(
            String aggregateType,
            long aggregateVersion,
            String eventType,
            Map<String, String> payload) {
        return new OutboxMessage(
                EVENT_ID,
                TENANT_ID,
                aggregateType,
                AGGREGATE_ID,
                aggregateVersion,
                eventType,
                (short) 1,
                payload,
                OCCURRED_AT,
                CORRELATION_ID,
                null);
    }
}
