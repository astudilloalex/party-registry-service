package com.alexastudillo.partyregistry.api;

import com.alexastudillo.partyregistry.api.response.ApiResponseCode;
import com.alexastudillo.partyregistry.api.response.ResponseManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies standard envelope construction independently of HTTP routing.
 */
class ResponseManagerTest {

    private final ResponseManager responseManager = new ResponseManager();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createdResponseKeepsHttpAndBodyStatusAligned() {
        try (Response response = responseManager.success(
                Response.Status.CREATED,
                Map.of("partyId", "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"))) {
            JsonNode body = objectMapper.valueToTree(response.getEntity());

            assertEquals(201, response.getStatus());
            assertEquals(201, body.get("status").asInt());
            assertEquals("successful", body.get("code").asText());
            assertEquals("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1", body.at("/data/partyId").asText());
            assertFalse(body.has("numberOfElements"));
        }
    }

    @Test
    void errorResponseNeverContainsData() {
        try (Response response = responseManager.error(
                Response.Status.CONFLICT,
                ApiResponseCode.VERSION_CONFLICT)) {
            JsonNode body = objectMapper.valueToTree(response.getEntity());

            assertEquals(409, response.getStatus());
            assertEquals(409, body.get("status").asInt());
            assertEquals("version-conflict", body.get("code").asText());
            assertFalse(body.has("data"));
        }
    }

    @Test
    void paginationFieldsAreLimitedToPageResponses() {
        try (Response response = responseManager.page(List.of("first", "second"), "next-page")) {
            JsonNode body = objectMapper.valueToTree(response.getEntity());

            assertEquals(2, body.get("numberOfElements").asInt());
            assertEquals("next-page", body.get("nextCursor").asText());
        }
    }

    @Test
    void successRejectsFailureStatuses() {
        assertThrows(IllegalArgumentException.class, () -> responseManager.success(
                Response.Status.BAD_REQUEST,
                Map.of("unexpected", true)));
    }
}
