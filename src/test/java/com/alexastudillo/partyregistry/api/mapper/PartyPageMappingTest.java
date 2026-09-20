package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.api.response.infrastructure.quarkus.ApiResponseObjectMapperCustomizer;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.model.response.PartySummaryResponse;
import com.alexastudillo.partyregistry.application.model.PartyPageResult;
import com.alexastudillo.partyregistry.application.model.PartySummaryResult;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies safe summary DTO projection, exact shared metadata, and explicit count-capacity failures. */
class PartyPageMappingTest {

    private final PartyApiMapper mapper = new PartyApiMapper();
    private final ResponseManager responses = new ResponseManager();
    private final ObjectMapper json = json();

    @Test
    void mapsBothTypesToExactlyTheAllowedSummaryFieldsWithHistoricalValues() {
        List<PartySummaryResult> items = List.of(summary(PartyType.NATURAL_PERSON), summary(PartyType.LEGAL_ENTITY));
        var page = new PartyPageResult(items, "next", "previous", 5, 3);
        List<PartySummaryResponse> data = mapper.toSummaryResponses(page);
        for (int index = 0; index < items.size(); index++) {
            var item = items.get(index);
            PartySummaryResponse response = data.get(index);
            JsonNode body = json.valueToTree(response);
            assertEquals(Set.of("partyId", "type", "displayName", "recordStatus", "createdAt", "version"),
                    body.properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet()));
            assertEquals(item.partyId().value().toString(), body.path("partyId").textValue());
            assertEquals(item.type().name(), body.path("type").textValue());
            assertEquals(item.displayName(), body.path("displayName").textValue());
            assertEquals("ARCHIVED", body.path("recordStatus").textValue());
            assertEquals(item.createdAt().toString(), body.path("createdAt").textValue());
            assertEquals(item.version().value(), body.path("version").longValue());
        }
        var envelope = responses.paginatedHttp(data, mapper.toPaginationMetadata(page));
        JsonNode body = json.valueToTree(envelope.getEntity());
        assertEquals(200, envelope.getStatus());
        assertEquals(envelope.getStatus(), body.path("status").intValue());
        assertEquals(5, body.path("totalElements").intValue());
        assertEquals(3, body.path("totalPages").intValue());
        assertEquals(2, body.path("numberOfElements").intValue());
        assertEquals("next", body.path("nextCursor").textValue());
        assertEquals("previous", body.path("prevCursor").textValue());
    }

    @Test
    void retainsZeroMetadataAndEmptyArrayForAnEmptyResult() {
        var page = new PartyPageResult(List.of(), null, null, 0, 0);
        var response = responses.paginatedHttp(mapper.toSummaryResponses(page), mapper.toPaginationMetadata(page));
        JsonNode body = json.valueToTree(response.getEntity());
        assertTrue(body.path("data").isArray());
        assertTrue(body.path("data").isEmpty());
        assertEquals(0, body.path("totalElements").intValue());
        assertEquals(0, body.path("totalPages").intValue());
        assertEquals(0, body.path("numberOfElements").intValue());
    }

    @Test
    void preservesTheLargestSharedCountAndRejectsUnrepresentableTotals() {
        var maximum = new PartyPageResult(List.of(), null, null, Integer.MAX_VALUE, Integer.MAX_VALUE);
        var response = responses.paginatedHttp(mapper.toSummaryResponses(maximum), mapper.toPaginationMetadata(maximum));
        JsonNode body = json.valueToTree(response.getEntity());
        assertEquals(Integer.MAX_VALUE, body.path("totalElements").intValue());
        assertEquals(Integer.MAX_VALUE, body.path("totalPages").intValue());
        var totalOverflow = new PartyPageResult(List.of(), null, null, (long) Integer.MAX_VALUE + 1, 1);
        var pagesOverflow = new PartyPageResult(List.of(), null, null, 1, (long) Integer.MAX_VALUE + 1);
        assertThrows(ArithmeticException.class, () -> mapper.toPaginationMetadata(totalOverflow));
        assertThrows(ArithmeticException.class, () -> mapper.toPaginationMetadata(pagesOverflow));
    }

    private static PartySummaryResult summary(PartyType type) {
        return new PartySummaryResult(new PartyId(UUID.randomUUID()), type, " Historical Áda %_ ", PartyRecordStatus.ARCHIVED,
                LocalDate.of(2026, Month.SEPTEMBER, 19).atTime(12, 0, 0, 123456000).toInstant(ZoneOffset.UTC), new PartyVersion(12));
    }

    private static ObjectMapper json() {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        new ApiResponseObjectMapperCustomizer().customize(mapper);
        return mapper;
    }
}
