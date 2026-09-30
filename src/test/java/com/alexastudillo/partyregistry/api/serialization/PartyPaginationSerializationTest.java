package com.alexastudillo.partyregistry.api.serialization;

import com.alexastudillo.api.response.contract.PaginationMetadata;
import com.alexastudillo.api.response.infrastructure.quarkus.ApiResponseObjectMapperCustomizer;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.error.GlobalErrorContractTestProfile;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.model.response.PartySummaryResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies pagination-only null cursor writers with the real CDI mapper and either shared-customizer registration order. */
@QuarkusTest
@TestProfile(GlobalErrorContractTestProfile.class)
class PartyPaginationSerializationTest {

    private static final Set<String> PAGE_KEYS = Set.of("status", "code", "data", "nextCursor", "prevCursor",
            "totalElements", "totalPages", "numberOfElements");

    @Inject
    ObjectMapper json;
    @Inject
    ResponseManager responses;

    @Test
    void managedMapperIncludesAllFiveMetadataKeysEvenOnAnEmptyPage() {
        var response = responses.paginatedHttp(List.<PartySummaryResponse>of(), new PaginationMetadata(null, null, 0, 0, 0));
        JsonNode body = json.valueToTree(response.getEntity());
        assertEquals(PAGE_KEYS, keys(body));
        assertEquals(200, response.getStatus());
        assertEquals(response.getStatus(), body.path("status").intValue());
        assertEquals("successful", body.path("code").textValue());
        assertTrue(body.path("nextCursor").isNull());
        assertTrue(body.path("prevCursor").isNull());
        assertTrue(body.path("data").isArray());
        assertTrue(body.path("data").isEmpty());
        assertEquals(0, body.path("totalElements").intValue());
        assertEquals(0, body.path("totalPages").intValue());
        assertEquals(0, body.path("numberOfElements").intValue());
    }

    @Test
    void firstMiddleAndTerminalPagesRetainPresentAndAbsentDirections() {
        for (PaginationMetadata metadata : List.of(new PaginationMetadata("next", null, 3, 3, 1),
                new PaginationMetadata("next", "previous", 3, 3, 1), new PaginationMetadata(null, "previous", 3, 3, 1))) {
            var response = responses.paginatedHttp(List.of(summary()), metadata);
            JsonNode body = json.valueToTree(response.getEntity());
            assertEquals(PAGE_KEYS, keys(body));
            assertEquals(json.valueToTree(response.getEntity().getNextCursor()), body.get("nextCursor"));
            assertEquals(json.valueToTree(response.getEntity().getPrevCursor()), body.get("prevCursor"));
        }
    }

    @Test
    void ordinarySuccessErrorsAndUnrelatedObjectsKeepTheirOriginalNullOmission() {
        assertEquals(Set.of("status", "code", "data"), keys(json.valueToTree(responses.successHttp(summary()).getEntity())));
        JsonNode error = json.valueToTree(responses.errorHttp(PartyResponseCode.BAD_REQUEST).getEntity());
        assertEquals(Set.of("status", "code"), keys(error));
        assertEquals(400, error.path("status").intValue());
        assertEquals("bad-request", error.path("code").textValue());
        assertEquals(Set.of("label"), keys(json.valueToTree(new Unrelated("retained", null, null))));
    }

    @Test
    void worksRegardlessOfSharedMixinCustomizerRegistrationOrder() {
        for (boolean serviceFirst : List.of(true, false)) {
            var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            var service = new PartyPaginationObjectMapperCustomizer();
            var shared = new ApiResponseObjectMapperCustomizer();
            if (serviceFirst) {
                service.customize(mapper);
                shared.customize(mapper);
            } else {
                shared.customize(mapper);
                service.customize(mapper);
            }
            JsonNode page = mapper.valueToTree(responses.paginatedHttp(List.of(), new PaginationMetadata(null, null, 0, 0, 0)).getEntity());
            assertEquals(PAGE_KEYS, keys(page));
            assertTrue(page.path("nextCursor").isNull());
            assertTrue(page.path("prevCursor").isNull());
            assertEquals(Set.of("status", "code"), keys(mapper.valueToTree(responses.errorHttp(PartyResponseCode.BAD_REQUEST).getEntity())));
        }
    }

    private static Set<String> keys(JsonNode body) {
        return body.properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private static PartySummaryResponse summary() {
        return new PartySummaryResponse(UUID.randomUUID(), "NATURAL_PERSON", "Label", "DRAFT",
                LocalDate.of(2026, Month.SEPTEMBER, 19).atStartOfDay().toInstant(ZoneOffset.UTC), 0);
    }

    /** Ensures identically named properties on unrelated payloads are not affected by the envelope customization. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Unrelated(String label, String nextCursor, String prevCursor) {
    }
}
