package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.api.response.contract.PaginationMetadata;
import com.alexastudillo.api.response.infrastructure.quarkus.ResponseManager;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.model.response.IdentifierSchemeResponse;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePage;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies approved DTO projection and actual CDI-managed shared-envelope serialization. */
@QuarkusTest
class IdentifierSchemeApiMapperTest {
    private static final Set<String> REQUIRED = Set.of("id", "code", "issuingCountryCode", "category",
            "applicableSubjectType", "name", "normalizerKey", "validatorKey", "requiresExpiration",
            "status", "version", "createdAt", "updatedAt");
    @Inject
    IdentifierSchemeApiMapper mapper;
    @Inject
    ObjectMapper json;
    @Inject
    ResponseManager responses;

    @Test
    void projectsExactPublicFieldsInEveryStateAndOmitsActorsAndAbsentOptionals() {
        for (var state : IdentifierSchemeStatus.values()) {
            for (boolean expiration : new boolean[]{true, false}) {
                var result = result(state, expiration, false);
                var response = mapper.toResponse(result);
                var tree = json.valueToTree(response);
                assertEquals(REQUIRED, fields(tree));
                assertEquals(result.id().value().toString(), tree.path("id").textValue());
                assertEquals(" Exact-Code ", tree.path("code").textValue());
                assertEquals(" Name ", tree.path("name").textValue());
                assertEquals("historical-normalizer", tree.path("normalizerKey").textValue());
                assertEquals("historical-validator", tree.path("validatorKey").textValue());
                assertEquals(state.name(), tree.path("status").textValue());
                assertTrue(tree.path("requiresExpiration").isBoolean());
                assertEquals(expiration, tree.path("requiresExpiration").booleanValue());
                assertEquals(Long.MAX_VALUE, tree.path("version").longValue());
                assertEquals(result.createdAt(), Instant.parse(tree.path("createdAt").textValue()));
                assertEquals(result.updatedAt(), Instant.parse(tree.path("updatedAt").textValue()));
                assertEquals("secret-creator", result.createdBy());
                assertEquals("secret-modifier", result.updatedBy());
            }
        }
    }

    @Test
    void serializesAllSuppliedOptionalFieldsWithoutNormalizationOrNarrowing() {
        var result = result(IdentifierSchemeStatus.DRAFT, true, true);
        var tree = json.valueToTree(mapper.toResponse(result));
        Set<String> expected = new HashSet<>(REQUIRED);
        expected.addAll(Set.of("description", "minimumLength", "maximumLength"));
        assertEquals(expected, fields(tree));
        assertEquals(" Description ", tree.path("description").textValue());
        assertEquals(1, tree.path("minimumLength").intValue());
        assertEquals(32767, tree.path("maximumLength").intValue());
        assertEquals("OTHER", tree.path("category").textValue());
        assertEquals("BOTH", tree.path("applicableSubjectType").textValue());
    }

    @Test
    void mapsBeforeWrappingAndMatchesHttpBodyStatusFor200And201() {
        var dto = mapper.toResponse(result(IdentifierSchemeStatus.DRAFT, false, false));
        for (var response : List.of(responses.successHttp(dto), responses.customHttp(PartyResponseCode.CREATED, dto))) {
            var tree = json.valueToTree(response.getEntity());
            assertEquals(Set.of("status", "code", "data"), fields(tree));
            assertEquals(response.getStatus(), tree.path("status").intValue());
            assertEquals("successful", tree.path("code").textValue());
            assertEquals(REQUIRED, fields(tree.path("data")));
            assertSame(dto, response.getEntity().getData());
        }
    }

    @Test
    void countOnlyPagesOmitUnavailableCursorsAndBothTotalsAndRetainListOrder() {
        var first = result(IdentifierSchemeStatus.RETIRED, true, true);
        var second = result(IdentifierSchemeStatus.DRAFT, false, false);
        for (boolean next : new boolean[]{false, true}) {
            for (boolean previous : new boolean[]{false, true}) {
                var page = new IdentifierSchemePage(List.of(first, second), next ? Optional.of("next") : Optional.empty(),
                        previous ? Optional.of("previous") : Optional.empty());
                var mapped = mapper.toResponses(page);
                assertEquals(List.of(first.id().value(), second.id().value()), mapped.stream().map(IdentifierSchemeResponse::id).toList());
                var metadata = mapper.toPagination(page);
                var response = responses.paginatedHttp(mapped, metadata);
                var tree = json.valueToTree(response.getEntity());
                Set<String> expected = new HashSet<>(Set.of("status", "code", "data", "numberOfElements"));
                if (next) {
                    expected.add("nextCursor");
                    assertEquals("next", tree.path("nextCursor").textValue());
                }
                if (previous) {
                    expected.add("prevCursor");
                    assertEquals("previous", tree.path("prevCursor").textValue());
                }
                assertEquals(expected, fields(tree));
                assertEquals(2, tree.path("numberOfElements").intValue());
                assertEquals(2, tree.path("data").size());
                assertEquals(response.getStatus(), tree.path("status").intValue());
                assertThrows(UnsupportedOperationException.class, mapped::clear);
            }
        }
        var empty = new IdentifierSchemePage(List.of(), Optional.empty(), Optional.empty());
        var tree = json.valueToTree(responses.paginatedHttp(mapper.toResponses(empty), mapper.toPagination(empty)).getEntity());
        assertEquals(Set.of("status", "code", "data", "numberOfElements"), fields(tree));
        assertEquals(0, tree.path("numberOfElements").intValue());
        assertTrue(tree.path("data").isArray());
        assertTrue(tree.path("data").isEmpty());
    }

    @Test
    void completeCountEnvelopesStillSerializeExplicitNullPartyNationalityCursors() {
        var response = responses.paginatedHttp(List.of(mapper.toResponse(result(IdentifierSchemeStatus.ACTIVE, true, false))),
                new PaginationMetadata(null, null, 1, 1, 1));
        var tree = json.valueToTree(response.getEntity());
        assertEquals(Set.of("status", "code", "data", "numberOfElements", "totalElements", "totalPages", "nextCursor", "prevCursor"), fields(tree));
        assertTrue(tree.path("nextCursor").isNull());
        assertTrue(tree.path("prevCursor").isNull());
        assertEquals(1, tree.path("totalElements").intValue());
        assertEquals(1, tree.path("totalPages").intValue());
    }

    private static IdentifierSchemeResult result(IdentifierSchemeStatus state, boolean expiration, boolean optional) {
        return new IdentifierSchemeResult(new IdentifierSchemeId(UUID.randomUUID()), " Exact-Code ", "EC",
                IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, " Name ", optional ? " Description " : null,
                "historical-normalizer", "historical-validator", optional ? 1 : null, optional ? 32767 : null,
                expiration, state, new IdentifierSchemeVersion(Long.MAX_VALUE), Instant.parse("2026-09-30T01:02:03.123456Z"),
                "secret-creator", Instant.parse("2026-10-01T02:03:04.654321Z"), "secret-modifier");
    }

    private static Set<String> fields(JsonNode node) {
        return node.properties().stream().map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
    }
}
