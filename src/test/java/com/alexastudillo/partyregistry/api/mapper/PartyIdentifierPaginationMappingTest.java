package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.partyregistry.api.error.GlobalErrorContractTestProfile;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.testing.PartyIdentifierFixtures;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies safe identifier DTOs and exact checked pagination through the managed shared HTTP response boundary. */
@QuarkusTest
@TestProfile(GlobalErrorContractTestProfile.class)
class PartyIdentifierPaginationMappingTest {

    private static final Set<String> PAGE_FIELDS = Set.of("status", "code", "data", "nextCursor", "prevCursor",
            "totalElements", "totalPages", "numberOfElements");
    private static final Set<String> REQUIRED_ITEM_FIELDS = Set.of("identifierId", "partyId", "identifierSchemeId",
            "schemeCode", "maskedValue", "status", "isPrimary", "version", "createdAt", "updatedAt");

    @Inject
    PartyIdentifierApiMapper mapper;

    @Inject
    ObjectMapper json;

    @Inject
    ResponseManager responses;

    @Test
    void mapsEveryDeclaredSafeFieldAndPreservesHistoricalMetadata() {
        PartyIdentifierResult result = safe(true);
        var response = responses.successHttp(mapper.toResponse(result));
        JsonNode body = json.valueToTree(response.getEntity());
        JsonNode data = body.path("data");
        assertEquals(200, response.getStatus());
        assertEquals(response.getStatus(), body.path("status").intValue());
        assertEquals("successful", body.path("code").textValue());
        assertEquals(Set.of("status", "code", "data"), fields(body));
        assertEquals(Set.of("identifierId", "partyId", "identifierSchemeId", "schemeCode", "maskedValue", "status",
                "isPrimary", "issuerCode", "issuedOn", "expiresOn", "verifiedAt", "verifiedBy", "version", "createdAt", "updatedAt"), fields(data));
        assertEquals("legacy-authority", data.path("issuerCode").textValue());
        assertEquals(result.version().value(), data.path("version").longValue());
        assertEquals(result.verifiedBy(), data.path("verifiedBy").textValue());
        assertEquals(json.valueToTree(result.verifiedAt()), data.path("verifiedAt"));
        assertFalse(body.toString().contains("fixture.ciphertext"));
    }

    @Test
    void optionalNullPropertiesDisappearWithoutAddingStorageReasonOrPaginationFields() {
        var response = responses.successHttp(mapper.toResponse(safe(false)));
        JsonNode body = json.valueToTree(response.getEntity());
        assertEquals(REQUIRED_ITEM_FIELDS, fields(body.path("data")));
        assertEquals(Set.of("status", "code", "data"), fields(body));
        assertEquals(Set.of("marker"), fields(json.valueToTree(new Unrelated("retained", null, null))));
        var error = responses.errorHttp(PartyResponseCode.BAD_REQUEST);
        JsonNode failure = json.valueToTree(error.getEntity());
        assertEquals(Set.of("status", "code"), fields(failure));
        assertEquals(error.getStatus(), failure.path("status").intValue());
        assertEquals("bad-request", failure.path("code").textValue());
    }

    @Test
    void emptyIdentifierPageRetainsAllFiveMetadataKeysAndExplicitNullDirections() {
        var page = new PartyIdentifierPageResult(List.of(), null, null, 0, 0);
        var response = responses.paginatedHttp(mapper.toResponses(page), mapper.toPagination(page));
        JsonNode body = json.valueToTree(response.getEntity());
        assertEquals(PAGE_FIELDS, fields(body));
        assertEquals(response.getStatus(), body.path("status").intValue());
        assertEquals("successful", body.path("code").textValue());
        assertTrue(body.path("data").isArray());
        assertTrue(body.path("data").isEmpty());
        assertTrue(body.path("nextCursor").isNull());
        assertTrue(body.path("prevCursor").isNull());
        assertEquals(0, body.path("totalElements").intValue());
        assertEquals(0, body.path("totalPages").intValue());
        assertEquals(0, body.path("numberOfElements").intValue());
    }

    @Test
    void pageMappingPreservesSafeItemsAvailableDirectionsAndBeforeContinuationCounts() {
        PartyIdentifierResult result = safe(true);
        for (var page : List.of(new PartyIdentifierPageResult(List.of(result), "next", null, 5, 3),
                new PartyIdentifierPageResult(List.of(result), "next", "previous", 5, 3),
                new PartyIdentifierPageResult(List.of(result), null, "previous", 5, 3))) {
            var response = responses.paginatedHttp(mapper.toResponses(page), mapper.toPagination(page));
            JsonNode body = json.valueToTree(response.getEntity());
            assertEquals(PAGE_FIELDS, fields(body));
            assertEquals(json.valueToTree(page.nextCursor()), body.path("nextCursor"));
            assertEquals(json.valueToTree(page.prevCursor()), body.path("prevCursor"));
            assertEquals(5, body.path("totalElements").intValue());
            assertEquals(3, body.path("totalPages").intValue());
            assertEquals(1, body.path("numberOfElements").intValue());
            assertEquals(json.valueToTree(mapper.toResponse(result)), body.path("data").get(0));
        }
    }

    @Test
    void emptyContinuationPreservesNonzeroTotalsWithoutInventingItems() {
        var page = new PartyIdentifierPageResult(List.of(), null, "previous", 5, 3);
        var response = responses.paginatedHttp(mapper.toResponses(page), mapper.toPagination(page));
        JsonNode body = json.valueToTree(response.getEntity());
        assertTrue(body.path("data").isEmpty());
        assertTrue(body.path("nextCursor").isNull());
        assertEquals("previous", body.path("prevCursor").textValue());
        assertEquals(5, body.path("totalElements").intValue());
        assertEquals(3, body.path("totalPages").intValue());
        assertEquals(0, body.path("numberOfElements").intValue());
    }

    @Test
    void mapsMaximumSupportedCountsExactly() {
        var page = new PartyIdentifierPageResult(List.of(safe(false)), null, null, Integer.MAX_VALUE, Integer.MAX_VALUE);
        var response = responses.paginatedHttp(mapper.toResponses(page), mapper.toPagination(page));
        JsonNode body = json.valueToTree(response.getEntity());
        assertEquals(Integer.MAX_VALUE, body.path("totalElements").intValue());
        assertEquals(Integer.MAX_VALUE, body.path("totalPages").intValue());
        assertEquals(1, body.path("numberOfElements").intValue());
    }

    @Test
    void rejectsUnsupportedCountsBeforeConstructingSharedMetadata() {
        long unsupported = (long) Integer.MAX_VALUE + 1;
        var totalOverflow = new PartyIdentifierPageResult(List.of(), null, null, unsupported, 1);
        var pagesOverflow = new PartyIdentifierPageResult(List.of(), null, null, 1, unsupported);
        var maximumLong = new PartyIdentifierPageResult(List.of(), null, null, Long.MAX_VALUE, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> mapper.toPagination(totalOverflow));
        assertThrows(ArithmeticException.class, () -> mapper.toPagination(pagesOverflow));
        assertThrows(ArithmeticException.class, () -> mapper.toPagination(maximumLong));
    }

    private static Set<String> fields(JsonNode node) {
        return node.properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private static PartyIdentifierResult safe(boolean metadata) {
        PartyIdentifierStatus status = metadata ? PartyIdentifierStatus.VERIFIED : PartyIdentifierStatus.PENDING_VERIFICATION;
        var base = PartyIdentifierFixtures.result(PartyIdentifierFixtures.identifier(PartyType.NATURAL_PERSON,
                status, metadata, 9, PartyIdentifierFixtures.EVALUATED_ON.plusDays(1)));
        return new PartyIdentifierResult(base.identifierId(), base.partyId(), base.identifierSchemeId(), base.schemeCode(),
                base.maskedValue(), base.status(), base.isPrimary(), metadata ? "legacy-authority" : null,
                metadata ? base.issuedOn() : null, metadata ? base.expiresOn() : null,
                base.verifiedAt(), base.verifiedBy(), base.version(), base.createdAt(), base.updatedAt());
    }

    /** Characterizes unrelated objects whose similarly named cursor fields must retain ordinary null omission. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Unrelated(String marker, String nextCursor, String prevCursor) { }
}
