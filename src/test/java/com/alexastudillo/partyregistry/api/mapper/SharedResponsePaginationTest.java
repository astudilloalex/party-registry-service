package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.api.response.contract.PaginationMetadata;
import com.alexastudillo.api.response.infrastructure.quarkus.ApiResponseObjectMapperCustomizer;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.model.response.NaturalPersonDetailsResponse;
import com.alexastudillo.partyregistry.api.model.response.PartyDetailResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes the resolved shared library independently of service pagination customization.
 */
class SharedResponsePaginationTest {

    private final ResponseManager responseManager = new ResponseManager();
    private final ObjectMapper objectMapper = sharedLibraryMapper();

    @Test
    void paginatedHttpPreservesTheStatusDataAndFlatMetadata() {
        var data = List.of(detail());
        var metadata = new PaginationMetadata("next-boundary", "previous-boundary", 3, 3, 1);
        var response = responseManager.paginatedHttp(data, metadata);
        JsonNode json = objectMapper.valueToTree(response.getEntity());

        assertEquals(200, response.getStatus());
        assertEquals(response.getStatus(), json.path("status").intValue());
        assertEquals("successful", json.path("code").textValue());
        assertEquals(data, response.getEntity().getData());
        assertEquals(Set.of("status", "code", "data", "nextCursor", "prevCursor",
                "totalElements", "totalPages", "numberOfElements"), fields(json));
        assertEquals("next-boundary", json.path("nextCursor").textValue());
        assertEquals("previous-boundary", json.path("prevCursor").textValue());
        assertEquals(3, json.path("totalElements").intValue());
        assertEquals(3, json.path("totalPages").intValue());
        assertEquals(1, json.path("numberOfElements").intValue());
    }

    @Test
    void defaultLibrarySerializationOmitsUnavailableCursorsButRetainsZeroCounts() {
        var metadata = new PaginationMetadata(null, null, 0, 0, 0);
        var response = responseManager.paginatedHttp(List.<PartyDetailResponse>of(), metadata);
        JsonNode json = objectMapper.valueToTree(response.getEntity());

        assertEquals(200, response.getStatus());
        assertEquals(response.getStatus(), json.path("status").intValue());
        assertEquals("successful", json.path("code").textValue());
        assertEquals(Set.of("status", "code", "data", "totalElements", "totalPages",
                "numberOfElements"), fields(json));
        assertTrue(json.path("data").isArray());
        assertTrue(json.path("data").isEmpty());
        assertEquals(0, json.path("totalElements").intValue());
        assertEquals(0, json.path("totalPages").intValue());
        assertEquals(0, json.path("numberOfElements").intValue());
    }

    @Test
    void ordinaryDetailAndErrorEnvelopesDoNotAcquirePagination() {
        var success = responseManager.successHttp(detail());
        var error = responseManager.errorHttp(PartyResponseCode.BAD_REQUEST);
        JsonNode successJson = objectMapper.valueToTree(success.getEntity());
        JsonNode errorJson = objectMapper.valueToTree(error.getEntity());

        assertEquals(200, success.getStatus());
        assertEquals(success.getStatus(), successJson.path("status").intValue());
        assertEquals("successful", successJson.path("code").textValue());
        assertEquals(Set.of("status", "code", "data"), fields(successJson));
        assertTrue(successJson.path("data").has("naturalPersonDetails"));
        assertFalse(successJson.path("data").has("legalEntityDetails"));
        assertEquals(400, error.getStatus());
        assertEquals(error.getStatus(), errorJson.path("status").intValue());
        assertEquals("bad-request", errorJson.path("code").textValue());
        assertEquals(Set.of("status", "code"), fields(errorJson));
    }

    @Test
    void checkedCountsPreserveTheLargestSupportedTotalWithoutWrapping() {
        long total = Integer.MAX_VALUE;
        long pages = Math.ceilDiv(total, 200);
        var metadata = checkedMetadata(total, pages, 1);
        var response = responseManager.paginatedHttp(List.of(detail()), metadata);
        JsonNode json = objectMapper.valueToTree(response.getEntity());

        assertEquals(total, json.path("totalElements").longValue());
        assertEquals(pages, json.path("totalPages").longValue());
        assertEquals(1, json.path("numberOfElements").intValue());
    }

    @Test
    void unrepresentableCountsFailBeforeConstructingSharedMetadata() {
        long unsupportedCount = (long) Integer.MAX_VALUE + 1;

        assertThrows(ArithmeticException.class, () -> checkedMetadata(unsupportedCount, 1, 1));
        assertThrows(ArithmeticException.class, () -> checkedMetadata(1, unsupportedCount, 1));
        assertThrows(ArithmeticException.class, () -> checkedMetadata(1, 1, unsupportedCount));
    }

    private static PaginationMetadata checkedMetadata(long total, long pages, long count) {
        return new PaginationMetadata(null, null,
                Math.toIntExact(total), Math.toIntExact(pages), Math.toIntExact(count));
    }

    private static Set<String> fields(JsonNode node) {
        return node.properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private static ObjectMapper sharedLibraryMapper() {
        var mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        new ApiResponseObjectMapperCustomizer().customize(mapper);
        return mapper;
    }

    private static PartyDetailResponse detail() {
        var createdAt = LocalDate.of(2026, Month.SEPTEMBER, 19).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new PartyDetailResponse(
                UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"),
                "NATURAL_PERSON", "ADA LOVELACE", "DRAFT", 0,
                createdAt, createdAt, "creator", "creator",
                new NaturalPersonDetailsResponse("ADA", "LOVELACE", null, null, null, null), null);
    }
}
