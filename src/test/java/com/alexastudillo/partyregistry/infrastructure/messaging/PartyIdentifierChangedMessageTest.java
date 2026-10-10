package com.alexastudillo.partyregistry.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies closed identifier maintenance event envelopes and typed primary flags at the broker boundary. */
class PartyIdentifierChangedMessageTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final OutboxMessageSerializer serializer = new OutboxMessageSerializer(mapper);

    @Test
    void serializesAllFiveNewNamesWithSafePayloadAndBooleanPrimary() throws Exception {
        Map<String, String> events = Map.of("party.identifier-updated.v1", "PENDING_VERIFICATION", "party.identifier-verified.v1", "VERIFIED",
                "party.identifier-rejected.v1", "REJECTED", "party.identifier-revoked.v1", "REVOKED", "party.identifier-primary-changed.v1", "VERIFIED");
        for (var entry : events.entrySet()) {
            var message = message(entry.getKey(), 9, payload(entry.getValue(), "false"));
            String encoded = serializer.serialize(message);
            var root = mapper.readTree(encoded);
            assertEquals(entry.getKey(), root.path("eventType").asText());
            assertTrue(root.path("payload").path("isPrimary").isBoolean());
            assertFalse(root.path("payload").path("isPrimary").booleanValue());
            assertEquals(4, root.path("payload").size());
            assertFalse(encoded.contains("reason"));
            assertFalse(encoded.contains("maskedValue"));
            assertFalse(encoded.contains("requestFingerprint"));
        }
    }

    @Test
    void rejectsUnknownPrivateAndIncoherentPayloadFields() {
        var valid = payload("VERIFIED", "true");
        for (Map<String, String> values : List.of(changed(valid, "reasonDetail", "sensitive-event-content"), changed(valid, "isPrimary", "TRUE"),
                changed(valid, "status", "REVOKED"), changed(valid, "partyId", "not-uuid"))) {
            var invalid = message("party.identifier-primary-changed.v1", 9, values);
            assertThrows(IllegalArgumentException.class, () -> serializer.serialize(invalid));
        }
        var invalidVersion = message("party.identifier-verified.v1", 0, valid);
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(invalidVersion));
    }

    private static Map<String, String> payload(String status, String primary) {
        return Map.of("partyId", UUID.randomUUID().toString(), "schemeCode", "FIXTURE_SCHEME", "status", status, "isPrimary", primary);
    }

    private static Map<String, String> changed(Map<String, String> original, String key, String value) {
        var result = new HashMap<>(original);
        result.put(key, value);
        return result;
    }

    private static OutboxMessage message(String eventType, long version, Map<String, String> payload) {
        var occurred = LocalDate.of(2026, Month.OCTOBER, 4).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new OutboxMessage(UUID.randomUUID(), UUID.randomUUID(), "PARTY_IDENTIFIER", UUID.randomUUID(), version, eventType, (short) 1,
                payload, occurred, UUID.randomUUID().toString(), null);
    }
}
