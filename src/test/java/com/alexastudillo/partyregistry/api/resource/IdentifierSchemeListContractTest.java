package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import com.alexastudillo.partyregistry.support.RootPartyFixtures;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.alexastudillo.partyregistry.api.resource.IdentifierSchemeHttpAssertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Verifies exact conjunctive listing, authenticated bounded traversal, and count-only shared HTTP pagination. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class IdentifierSchemeListContractTest {
    @Inject RootPartyFixtures parties;

    @Test
    void rejectsRealOtherResourceCursorsAndRetainsCompleteCountPartySerialization() {
        var context = Context.fresh();
        for (int index = 0; index < 2; index++) {
            RootPartyFixtures.await(() -> parties.create(UUID.fromString(context.tenant()), PartyType.LEGAL_ENTITY,
                    PartyRecordStatus.DRAFT, "Cursor source", java.time.Instant.now()));
        }
        Response source = context.request().queryParam("limit", 1).get("/v1/parties");
        assertEquals(200, source.statusCode());
        assertEquals(2, source.jsonPath().getInt("totalElements"));
        assertEquals(2, source.jsonPath().getInt("totalPages"));
        assertTrue(source.jsonPath().getMap("$").containsKey("prevCursor"));
        assertNull(source.jsonPath().get("prevCursor"));
        String token = source.jsonPath().getString("nextCursor");
        assertTrue(token != null && !token.isBlank());
        error(context.request().queryParams("limit", 1, "cursor", token).get(ROOT), 400, "bad-request", context);
    }

    @Test
    void intersectsFiltersWithoutBothWideningAndReturnsEmptyOrAllStatePages() {
        var context = Context.fresh();
        Map<String, Object> expected = null;
        for (String subject : List.of("NATURAL_PERSON", "BOTH", "LEGAL_ENTITY")) {
            var row = success(context.request().contentType("application/json").header("Idempotency-Key", UUID.randomUUID().toString())
                    .body(body(uniqueCode()).replace("ZZ", "QZ").replace("BOTH", subject)).post(ROOT), 201, context);
            if (subject.equals("NATURAL_PERSON")) expected = row;
        }
        var matches = page(context.request().queryParams("issuingCountryCode", "QZ", "category", "OTHER",
                "applicableSubjectType", "NATURAL_PERSON", "status", "DRAFT").get(ROOT), context, false, false);
        assertEquals(List.of(expected), matches);
        for (var data : matches) {
            assertEquals("QZ", data.get("issuingCountryCode"));
            assertEquals("OTHER", data.get("category"));
            assertEquals("NATURAL_PERSON", data.get("applicableSubjectType"));
            assertEquals("DRAFT", data.get("status"));
        }
        assertTrue(page(context.request().queryParams("issuingCountryCode", "QW", "category", "PASSPORT",
                "applicableSubjectType", "LEGAL_ENTITY", "status", "RETIRED").get(ROOT), context, false, false).isEmpty());
        Response all = context.request().get(ROOT);
        List<Map<String, Object>> data = all.jsonPath().getList("data");
        assertTrue(data.stream().map(row -> row.get("status")).collect(java.util.stream.Collectors.toSet())
                .containsAll(Set.of("DRAFT", "ACTIVE", "DEPRECATED", "RETIRED")));
        page(all, context, all.jsonPath().getString("nextCursor") != null, false);
    }

    @Test
    void appliesDefaultAndMaximumAndReproducesPriorPagesWithoutDuplicates() {
        var context = Context.fresh();
        var created = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < 205; index++) {
            created.add(success(context.request().contentType("application/json").header("Idempotency-Key", UUID.randomUUID().toString())
                    .body(body(uniqueCode()).replace("ZZ", "QX").replace("OTHER", "PASSPORT")).post(ROOT), 201, context));
        }
        var request = context.request().queryParams("issuingCountryCode", "QX", "category", "PASSPORT");
        assertEquals(created.subList(0, 50), page(request.get(ROOT), context, true, false));
        Response first = context.request().queryParams("issuingCountryCode", "QX", "category", "PASSPORT", "limit", 200).get(ROOT);
        assertEquals(created.subList(0, 200), page(first, context, true, false));
        String next = first.jsonPath().getString("nextCursor");
        Response last = context.request().queryParams("issuingCountryCode", "QX", "category", "PASSPORT", "limit", 200, "cursor", next).get(ROOT);
        assertEquals(created.subList(200, 205), page(last, context, false, true));
        Response previous = context.request().queryParams("issuingCountryCode", "QX", "category", "PASSPORT", "limit", 200,
                "cursor", last.jsonPath().getString("prevCursor")).get(ROOT);
        assertEquals(created.subList(0, 200), page(previous, context, true, false));
        assertEquals(205, created.stream().map(row -> row.get("id")).distinct().count());
        assertEquals(created.subList(0, 1), page(context.request().queryParams("issuingCountryCode", "QX", "category", "PASSPORT", "limit", 1).get(ROOT), context, true, false));
        for (String token : List.of(next.substring(0, next.length() - 1), "invalid", "v1.unknown.invalid.invalid")) {
            error(context.request().queryParams("issuingCountryCode", "QX", "category", "PASSPORT", "limit", 200, "cursor", token).get(ROOT),
                    400, "bad-request", context);
        }
        var other = Context.fresh();
        error(other.request().queryParams("issuingCountryCode", "QX", "category", "PASSPORT", "limit", 200, "cursor", next).get(ROOT), 400, "bad-request", other);
        for (var changed : List.of(Map.of("issuingCountryCode", "QY", "category", "PASSPORT", "limit", "200"),
                Map.of("issuingCountryCode", "QX", "category", "OTHER", "limit", "200"),
                Map.of("issuingCountryCode", "QX", "category", "PASSPORT", "limit", "199"),
                Map.of("issuingCountryCode", "QX", "category", "PASSPORT", "limit", "200", "status", "DRAFT"),
                Map.of("issuingCountryCode", "QX", "category", "PASSPORT", "limit", "200", "applicableSubjectType", "BOTH"))) {
            error(context.request().queryParams(changed).queryParam("cursor", next).get(ROOT), 400, "bad-request", context);
        }
    }

    @Test
    void rejectsTheWholeDecodedQueryInsteadOfIgnoringUnknownRepeatedOrBlankValues() {
        var context = Context.fresh();
        verifyMalformedAndInvalidQueryParams(context);
        verifyDuplicatedQueryParams(context);
    }

    private static void verifyMalformedAndInvalidQueryParams(Context context) {
        for (String query : List.of("unknown=1", "status=DRAFT&status=DRAFT", "limit=1&limit=1", "issuingCountryCode=ec",
                "category=other", "applicableSubjectType=UNKNOWN", "status=unknown", "limit=", "limit=0", "limit=-1", "limit=201",
                "limit=1.0", "limit=+1", "limit=999999999999999999999", "cursor=", "category=", "status=DRAFT&unknown=1")) {
            error(context.request().get(ROOT + "?" + query), 400, "bad-request", context);
        }
    }

    private static void verifyDuplicatedQueryParams(Context context) {
        for (String parameter : List.of("issuingCountryCode", "category", "applicableSubjectType", "status", "cursor", "limit")) {
            error(context.request().queryParam(parameter, "a", "b").get(ROOT), 400, "bad-request", context);
        }
    }

    private static List<Map<String, Object>> page(Response response, Context context, boolean next, boolean previous) {
        assertEquals(200, response.statusCode(), response.asString());
        assertEquals(200, response.jsonPath().getInt("status"));
        assertEquals("successful", response.jsonPath().getString("code"));
        assertEquals(context.process(), response.header("Process-Id"));
        var fields = new HashSet<>(Set.of("status", "code", "data", "numberOfElements"));
        if (next) fields.add("nextCursor");
        if (previous) fields.add("prevCursor");
        assertEquals(fields, response.jsonPath().getMap("$").keySet());
        List<Map<String, Object>> data = response.jsonPath().getList("data");
        data.forEach(IdentifierSchemeHttpAssertions::safe);
        assertEquals(data.size(), response.jsonPath().getInt("numberOfElements"));
        return data;
    }
}
