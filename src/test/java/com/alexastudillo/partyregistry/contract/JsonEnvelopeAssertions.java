package com.alexastudillo.partyregistry.contract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JsonEnvelopeAssertions {
    private JsonEnvelopeAssertions() {
    }

    static void assertSuccessPage(String body) {
        JsonNode json;
        try {
            json = ContractSources.JSON.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Response is not JSON", exception);
        }
        assertEquals(Set.of("status", "code", "data", "pagination"),
                ContractSources.JSON.convertValue(json, java.util.Map.class).keySet());
        assertEquals("success", json.path("status").asText());
        assertTrue(json.path("data").isArray());
        assertEquals(0, json.path("pagination").path("page").asInt());
        assertEquals(20, json.path("pagination").path("size").asInt());
    }
}
