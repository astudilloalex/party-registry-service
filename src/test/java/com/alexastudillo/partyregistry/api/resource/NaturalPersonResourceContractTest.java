package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.api.filter.RequestContextFilter;
import com.alexastudillo.partyregistry.application.command.CreateNaturalPersonCommand;
import com.alexastudillo.partyregistry.infrastructure.integration.geographic.GeographicReferenceStubResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.vertx.VertxContextSupport;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the complete natural-person HTTP contract against PostgreSQL and the
 * controlled Geographic Reference boundary.
 */
@QuarkusTest
@TestProfile(NaturalPersonResourceContractTest.ContractProfile.class)
@QuarkusTestResource(value = GeographicReferenceStubResource.class, restrictToAnnotatedClass = true)
class NaturalPersonResourceContractTest {

    private static final String TENANT_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String PROCESS_ID = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";
    private static final String USER_ID = "geographic-reference-adapter-test";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IF_MATCH_HEADER = "If-Match";
    private static final String RESOURCE_PATH = "/v1/natural-person";
    private static final Duration MAXIMUM_WAIT = Duration.ofSeconds(10);
    private static final Set<String> SUCCESS_ENVELOPE_FIELDS = Set.of("status", "code", "data");
    private static final Set<String> ERROR_ENVELOPE_FIELDS = Set.of("status", "code");
    private static final Set<String> COMPLETE_DATA_FIELDS = Set.of(
            "partyId",
            "type",
            "displayName",
            "recordStatus",
            "version",
            "createdAt",
            "updatedAt",
            "createdBy",
            "updatedBy",
            "naturalPersonDetails");
    private static final Set<String> COMPLETE_DETAILS_FIELDS = Set.of(
            "givenNames",
            "familyNames",
            "preferredName",
            "birthDate",
            "dateOfDeath",
            "birthCountryCode");

    private static final String COMPLETE_CREATE_BODY = """
            {
              "displayName": "The Countess of Lovelace",
              "givenNames": "Ada",
              "familyNames": "Lovelace",
              "preferredName": "Ada",
              "birthDate": "1815-12-10",
              "dateOfDeath": "1852-11-27",
              "birthCountryCode": "EC"
            }
            """;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    void verifiesCreationWithExplicitPayload() {
        UUID tenantId = UUID.fromString(TENANT_ID);

        Map<String, Object> explicit = assertSuccess(
                create(tenantId, key("explicit"), COMPLETE_CREATE_BODY),
                201);
        assertEquals("The Countess of Lovelace", explicit.get("displayName"));
        assertEquals("NATURAL_PERSON", explicit.get("type"));
        assertEquals("DRAFT", explicit.get("recordStatus"));
        assertEquals(0, number(explicit, "version"));
        assertEquals(USER_ID, explicit.get("createdBy"));
        assertEquals(USER_ID, explicit.get("updatedBy"));
        assertNotNull(explicit.get("createdAt"));
        assertNotNull(explicit.get("updatedAt"));
        Instant.parse(string(explicit, "createdAt"));
        Instant.parse(string(explicit, "updatedAt"));
        assertEquals(explicit.get("createdAt"), explicit.get("updatedAt"));
        assertEquals(7, UUID.fromString(string(explicit, "partyId")).version());

        Map<String, Object> explicitDetails = nested(explicit, "naturalPersonDetails");
        assertEquals("Ada", explicitDetails.get("givenNames"));
        assertEquals("Lovelace", explicitDetails.get("familyNames"));
        assertEquals("Ada", explicitDetails.get("preferredName"));
        assertEquals("1815-12-10", explicitDetails.get("birthDate"));
        assertEquals("1852-11-27", explicitDetails.get("dateOfDeath"));
        assertEquals("EC", explicitDetails.get("birthCountryCode"));
    }

    @Test
    void verifiesCreationWithDerivedDisplayNameAndNullableFields() {
        UUID tenantId = UUID.fromString(TENANT_ID);

        String nullableBody = """
                {
                  "displayName": null,
                  "givenNames": "  Katherine  ",
                  "familyNames": "  Johnson  ",
                  "preferredName": null,
                  "birthDate": null,
                  "dateOfDeath": null,
                  "birthCountryCode": null
                }
                """;
        Map<String, Object> derived = assertSuccess(
                create(tenantId, key("derived"), nullableBody),
                201);
        assertEquals("Katherine Johnson", derived.get("displayName"));
        Map<String, Object> derivedDetails = nested(derived, "naturalPersonDetails");
        assertEquals("  Katherine  ", derivedDetails.get("givenNames"));
        assertEquals("  Johnson  ", derivedDetails.get("familyNames"));
        assertEquals(COMPLETE_DETAILS_FIELDS, derivedDetails.keySet());
        assertNull(derivedDetails.get("preferredName"));
        assertNull(derivedDetails.get("birthDate"));
        assertNull(derivedDetails.get("dateOfDeath"));
        assertNull(derivedDetails.get("birthCountryCode"));
    }

    @Test
    void verifiesCreationValidationCountryOutcomesAndAtomicRejection() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        UUID validationTenant = UUID.randomUUID();

        assertRejectedCreationDoesNotPersist(
                validationTenant,
                key("missing-name"),
                "{\"givenNames\":\"Ada\"}",
                400,
                "bad-request");
        assertRejectedCreationDoesNotPersist(
                validationTenant,
                key("invalid-country-format"),
                "{\"givenNames\":\"Ada\",\"familyNames\":\"Lovelace\",\"birthCountryCode\":\"ecu\"}",
                400,
                "bad-request");
        assertRejectedCreationDoesNotPersist(
                validationTenant,
                key("unknown-property"),
                "{\"givenNames\":\"Ada\",\"familyNames\":\"Lovelace\",\"unsupported\":true}",
                400,
                "bad-request");
        assertRejectedCreationDoesNotPersist(
                validationTenant,
                key("invalid-date-format"),
                "{\"givenNames\":\"Ada\",\"familyNames\":\"Lovelace\",\"birthDate\":\"not-a-date\"}",
                400,
                "bad-request");
        assertRejectedCreationDoesNotPersist(
                validationTenant,
                key("future-birth"),
                "{\"givenNames\":\"Future\",\"familyNames\":\"Person\",\"birthDate\":\"2999-01-01\"}",
                422,
                "unprocessable-entity");
        assertRejectedCreationDoesNotPersist(
                validationTenant,
                key("death-before-birth"),
                "{\"givenNames\":\"Invalid\",\"familyNames\":\"Dates\",\"birthDate\":\"2000-01-02\",\"dateOfDeath\":\"2000-01-01\"}",
                422,
                "unprocessable-entity");
        assertRejectedCreationDoesNotPersist(
                tenantId,
                key("unknown-country"),
                createBody("Unknown", "Country", "ZZ"),
                422,
                "unprocessable-entity");
        assertRejectedCreationDoesNotPersist(
                tenantId,
                key("unavailable-country"),
                createBody("Unavailable", "Country", "SE"),
                503,
                "dependency-unavailable");
    }

    @Test
    void verifiesIdempotencyHeaderValidationAndNonPersistence() {
        UUID invalidTenant = UUID.randomUUID();
        String body = createBody("Idempotent", "Person", null);
        long initialRows = countParties(invalidTenant);

        assertError(request(invalidTenant).body(body).post(RESOURCE_PATH), 400, "bad-request");
        assertError(
                request(invalidTenant)
                        .header(IDEMPOTENCY_KEY_HEADER, "duplicate", "duplicate")
                        .body(body)
                        .post(RESOURCE_PATH),
                400,
                "bad-request");
        assertError(create(invalidTenant, "   ", body), 400, "bad-request");
        assertError(create(invalidTenant, "x".repeat(129), body), 400, "bad-request");
        assertEquals(initialRows, countParties(invalidTenant));
    }

    @Test
    void verifiesIdempotencySequentialReplayAndPayloadConflict() {
        String body = createBody("Idempotent", "Person", null);
        UUID replayTenant = UUID.randomUUID();
        String replayKey = key("sequential-replay");
        Map<String, Object> original = assertSuccess(create(replayTenant, replayKey, body), 201);
        Map<String, Object> replay = assertSuccess(create(replayTenant, replayKey, body), 201);
        assertEquals(original, replay);
        assertEquals(1, countParties(replayTenant));
        assertEquals(1, countIdempotencyRecords(replayTenant, replayKey));

        assertError(
                create(replayTenant, replayKey, createBody("Different", "Payload", null)),
                409,
                "conflict");
        assertEquals(1, countParties(replayTenant));
        assertEquivalentData(original, getData(replayTenant, string(original, "partyId")));
    }

    @Test
    void verifiesIdempotencyKeyIsScopedByTenant() {
        String body = createBody("Idempotent", "Person", null);
        String scopedKey = key("tenant-scope");
        UUID firstTenant = UUID.randomUUID();
        UUID secondTenant = UUID.randomUUID();
        Map<String, Object> firstTenantResult = assertSuccess(create(firstTenant, scopedKey, body), 201);
        Map<String, Object> secondTenantResult = assertSuccess(create(secondTenant, scopedKey, body), 201);
        assertNotEquals(firstTenantResult.get("partyId"), secondTenantResult.get("partyId"));
        assertEquals(1, countParties(firstTenant));
        assertEquals(1, countParties(secondTenant));
    }

    @Test
    void verifiesConcurrentIdenticalCreationYieldsEqualResponse() throws Exception {
        String body = createBody("Idempotent", "Person", null);
        UUID concurrentTenant = UUID.randomUUID();
        String concurrentKey = key("concurrent-replay");
        List<Response> responses = race(
                () -> create(concurrentTenant, concurrentKey, body),
                () -> create(concurrentTenant, concurrentKey, body));
        Map<String, Object> first = assertSuccess(responses.get(0), 201);
        Map<String, Object> second = assertSuccess(responses.get(1), 201);
        assertEquals(first, second);
        UUID partyId = UUID.fromString(string(first, "partyId"));
        assertEquals(1, countParties(concurrentTenant));
        assertEquals(1, countDetails(partyId));
        assertEquals(1, countIdempotencyRecords(concurrentTenant, concurrentKey));
    }

    @Test
    void verifiesRetrievalMappingAndConcealsAbsentCrossTenantAndLegalRows() {
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> created = assertSuccess(
                create(tenantId, key("retrieval"), createBody("Dorothy", "Vaughan", null)),
                201);
        String partyId = string(created, "partyId");

        assertEquivalentData(created, getData(tenantId, partyId));
        assertError(request(tenantId).get(RESOURCE_PATH + "/not-a-uuid"), 400, "bad-request");

        Map<String, Object> absent = assertError(
                request(tenantId).get(RESOURCE_PATH + "/" + UUID.randomUUID()),
                404,
                "not-found");
        Map<String, Object> crossTenant = assertError(
                request(UUID.randomUUID()).get(RESOURCE_PATH + "/" + partyId),
                404,
                "not-found");

        UUID legalEntityId = UUID.randomUUID();
        persistLegalEntity(tenantId, legalEntityId);
        Map<String, Object> legalEntity = assertError(
                request(tenantId).get(RESOURCE_PATH + "/" + legalEntityId),
                404,
                "not-found");
        assertEquals(absent, crossTenant);
        assertEquals(absent, legalEntity);
    }

    @Test
    void verifiesReplacementHeaderAndPayloadValidation() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        String initialBody = """
                {
                  "displayName": "Admiral Hopper",
                  "givenNames": "Grace",
                  "familyNames": "Murray Hopper",
                  "preferredName": "Amazing Grace",
                  "birthDate": "1906-12-09",
                  "dateOfDeath": "1992-01-01"
                }
                """;
        Map<String, Object> created = assertSuccess(
                create(tenantId, key("put-validation"), initialBody),
                201);
        String partyId = string(created, "partyId");
        Map<String, Object> persistedBeforeReplacement = getData(tenantId, partyId);

        assertError(
                put(tenantId, partyId, "0", "{\"givenNames\":\"Grace\"}"),
                400,
                "bad-request");
        assertError(
                request(tenantId)
                        .body("{\"givenNames\":\"Grace\",\"familyNames\":\"Hopper\"}")
                        .put(RESOURCE_PATH + "/" + partyId),
                400,
                "bad-request");
        assertError(
                put(
                        tenantId,
                        partyId,
                        "0",
                        "{\"givenNames\":\"Grace\",\"familyNames\":\"Hopper\",\"unsupported\":true}"),
                400,
                "bad-request");
        assertError(
                request(tenantId)
                        .header(IF_MATCH_HEADER, "0", "0")
                        .body("{\"givenNames\":\"Grace\",\"familyNames\":\"Hopper\"}")
                        .put(RESOURCE_PATH + "/" + partyId),
                400,
                "bad-request");
        for (String malformedVersion : List.of("-1", "01", "1.0", "9223372036854775808")) {
            assertError(
                    put(
                            tenantId,
                            partyId,
                            malformedVersion,
                            "{\"givenNames\":\"Grace\",\"familyNames\":\"Hopper\"}"),
                    400,
                    "bad-request");
        }
        assertEquals(persistedBeforeReplacement, getData(tenantId, partyId));
    }

    @Test
    void verifiesCompleteReplacementFieldMappingAndAtomicVersioning() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        String initialBody = """
                {
                  "displayName": "Admiral Hopper",
                  "givenNames": "Grace",
                  "familyNames": "Murray Hopper",
                  "preferredName": "Amazing Grace",
                  "birthDate": "1906-12-09",
                  "dateOfDeath": "1992-01-01"
                }
                """;
        Map<String, Object> created = assertSuccess(
                create(tenantId, key("put-replacement"), initialBody),
                201);
        String partyId = string(created, "partyId");

        String replacementBody = """
                {
                  "givenNames": "Grace",
                  "familyNames": "Hopper",
                  "birthCountryCode": "EC"
                }
                """;
        Map<String, Object> replaced = assertSuccess(
                put(tenantId, partyId, "0", replacementBody),
                200);
        assertEquals("Grace Hopper", replaced.get("displayName"));
        assertEquals(1, number(replaced, "version"));
        assertTimestampEquivalent(created.get("createdAt"), replaced.get("createdAt"));
        assertEquals(created.get("createdBy"), replaced.get("createdBy"));
        assertEquals(USER_ID, replaced.get("updatedBy"));
        Map<String, Object> replacedDetails = nested(replaced, "naturalPersonDetails");
        assertEquals("EC", replacedDetails.get("birthCountryCode"));
        assertNull(replacedDetails.get("preferredName"));
        assertNull(replacedDetails.get("birthDate"));
        assertNull(replacedDetails.get("dateOfDeath"));

        assertError(put(tenantId, partyId, "0", replacementBody), 412, "precondition-failed");
        assertEquals(replaced, getData(tenantId, partyId));
    }

    @Test
    void verifiesReplacementCountryValidationAndErrorOutcomes() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        String initialBody = """
                {
                  "displayName": "Admiral Hopper",
                  "givenNames": "Grace",
                  "familyNames": "Murray Hopper",
                  "preferredName": "Amazing Grace",
                  "birthDate": "1906-12-09",
                  "dateOfDeath": "1992-01-01"
                }
                """;
        Map<String, Object> created = assertSuccess(
                create(tenantId, key("put-outcomes"), initialBody),
                201);
        String partyId = string(created, "partyId");

        String replacementBody = """
                {
                  "givenNames": "Grace",
                  "familyNames": "Hopper",
                  "birthCountryCode": "EC"
                }
                """;
        Map<String, Object> replaced = assertSuccess(
                put(tenantId, partyId, "0", replacementBody),
                200);

        String invalidDates = """
                {
                  "givenNames": "Grace",
                  "familyNames": "Hopper",
                  "birthDate": "2000-01-02",
                  "dateOfDeath": "2000-01-01"
                }
                """;
        assertError(put(tenantId, partyId, "1", invalidDates), 422, "unprocessable-entity");
        assertError(
                put(tenantId, partyId, "1", createBody("Grace", "Hopper", "ZZ")),
                422,
                "unprocessable-entity");
        assertError(
                put(tenantId, partyId, "1", createBody("Grace", "Hopper", "SE")),
                503,
                "dependency-unavailable");
        assertEquals(replaced, getData(tenantId, partyId));

        assertError(
                put(tenantId, UUID.randomUUID().toString(), "0", replacementBody),
                404,
                "not-found");
    }

    @Test
    void verifiesPatchTriStateFieldUpdatesAndDisplayNameDerivation() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        String initialBody = """
                {
                  "displayName": "NASA Mathematician",
                  "givenNames": "Katherine",
                  "familyNames": "Johnson",
                  "preferredName": "Katherine",
                  "birthDate": "1918-08-26",
                  "dateOfDeath": "2020-02-24"
                }
                """;
        Map<String, Object> created = assertSuccess(
                create(tenantId, key("patch-tristate"), initialBody),
                201);
        String partyId = string(created, "partyId");

        assertError(
                request(tenantId)
                        .body("{\"preferredName\":\"Kathy\"}")
                        .patch(RESOURCE_PATH + "/" + partyId),
                400,
                "bad-request");

        Map<String, Object> preferred = assertSuccess(
                patch(tenantId, partyId, "0", "{\"preferredName\":\"Kathy\"}"),
                200);
        assertEquals(1, number(preferred, "version"));
        assertEquals("NASA Mathematician", preferred.get("displayName"));
        Map<String, Object> preferredDetails = nested(preferred, "naturalPersonDetails");
        assertEquals("Kathy", preferredDetails.get("preferredName"));
        assertEquals("1918-08-26", preferredDetails.get("birthDate"));
        assertEquals("2020-02-24", preferredDetails.get("dateOfDeath"));

        Map<String, Object> cleared = assertSuccess(
                patch(tenantId, partyId, "1", "{\"preferredName\":null}"),
                200);
        assertEquals(2, number(cleared, "version"));
        assertNull(nested(cleared, "naturalPersonDetails").get("preferredName"));

        Map<String, Object> renamed = assertSuccess(
                patch(tenantId, partyId, "2", "{\"familyNames\":\"Gobble Johnson\"}"),
                200);
        assertEquals(3, number(renamed, "version"));
        assertEquals("Katherine Gobble Johnson", renamed.get("displayName"));
        assertEquals("Katherine", nested(renamed, "naturalPersonDetails").get("givenNames"));

        Map<String, Object> countryChanged = assertSuccess(
                patch(tenantId, partyId, "3", "{\"birthCountryCode\":\"EC\"}"),
                200);
        assertEquals(4, number(countryChanged, "version"));
        assertEquals("EC", nested(countryChanged, "naturalPersonDetails").get("birthCountryCode"));
    }

    @Test
    void verifiesPatchPayloadValidationAndErrorOutcomes() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        String initialBody = """
                {
                  "displayName": "NASA Mathematician",
                  "givenNames": "Katherine",
                  "familyNames": "Johnson",
                  "birthDate": "1918-08-26",
                  "dateOfDeath": "2020-02-24"
                }
                """;
        Map<String, Object> created = assertSuccess(
                create(tenantId, key("patch-validation"), initialBody),
                201);
        String partyId = string(created, "partyId");
        Map<String, Object> persistedBeforePatch = getData(tenantId, partyId);

        assertError(patch(tenantId, partyId, "0", "{}"), 400, "bad-request");
        assertError(
                patch(tenantId, partyId, "0", "{\"unsupported\":\"value\"}"),
                400,
                "bad-request");
        assertError(
                patch(tenantId, partyId, "0", "{\"birthDate\":\"2021-01-01\"}"),
                422,
                "unprocessable-entity");
        assertError(
                patch(tenantId, partyId, "0", "{\"birthCountryCode\":\"ZZ\"}"),
                422,
                "unprocessable-entity");
        assertError(
                patch(tenantId, partyId, "0", "{\"birthCountryCode\":\"SE\"}"),
                503,
                "dependency-unavailable");
        assertEquals(persistedBeforePatch, getData(tenantId, partyId));
    }

    @Test
    void permitsExactlyOneWinnerForConcurrentSameVersionUpdates() throws Exception {
        for (int iteration = 0; iteration < 3; iteration++) {
            UUID tenantId = UUID.randomUUID();
            Map<String, Object> created = assertSuccess(
                    create(
                            tenantId,
                            key("concurrent-update-" + iteration),
                            createBody("Concurrent", "Person", null)),
                    201);
            String partyId = string(created, "partyId");
            String firstPreference = "First Winner " + iteration;
            String secondPreference = "Second Winner " + iteration;

            List<Response> responses = race(
                    () -> patch(
                            tenantId,
                            partyId,
                            "0",
                            "{\"preferredName\":\"" + firstPreference + "\"}"),
                    () -> patch(
                            tenantId,
                            partyId,
                            "0",
                            "{\"preferredName\":\"" + secondPreference + "\"}"));
            List<Response> winners = responses.stream().filter(response -> response.statusCode() == 200).toList();
            List<Response> losers = responses.stream().filter(response -> response.statusCode() == 412).toList();
            assertEquals(1, winners.size());
            assertEquals(1, losers.size());

            Map<String, Object> winningData = assertSuccess(winners.getFirst(), 200);
            assertError(losers.getFirst(), 412, "precondition-failed");
            Map<String, Object> persisted = getData(tenantId, partyId);
            assertEquals(1, number(persisted, "version"));
            assertEquals(winningData, persisted);
            String persistedPreference = string(nested(persisted, "naturalPersonDetails"), "preferredName");
            assertTrue(persistedPreference.equals(firstPreference) || persistedPreference.equals(secondPreference));
        }
    }

    @Test
    void verifiesResponseEnvelopeEqualityProcessEchoAndSanitizedFailures() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        String key = key("response-boundary");
        Response createdResponse = create(tenantId, key, COMPLETE_CREATE_BODY);
        Map<String, Object> created = assertSuccess(createdResponse, 201);
        assertEquals(COMPLETE_DATA_FIELDS, created.keySet());
        assertFalse(created.containsKey("tenantId"));
        assertFalse(created.containsKey("auditInfo"));
        assertFalse(created.containsKey("details"));
        assertFalse(created.containsKey("class"));

        String partyId = string(created, "partyId");
        assertSuccess(request(tenantId).get(RESOURCE_PATH + "/" + partyId), 200);
        assertSanitizedError(
                request(tenantId).get(RESOURCE_PATH + "/invalid-id"),
                400,
                "bad-request");
        assertSanitizedError(
                request(tenantId).get(RESOURCE_PATH + "/" + UUID.randomUUID()),
                404,
                "not-found");
        assertSanitizedError(
                request(tenantId).delete(RESOURCE_PATH + "/" + partyId),
                405,
                "method-not-allowed");
        assertSanitizedError(
                create(tenantId, key, createBody("Conflicting", "Payload", null)),
                409,
                "conflict");
        assertSanitizedError(
                create(tenantId, key("response-unavailable"), createBody("Remote", "Failure", "SE")),
                503,
                "dependency-unavailable");
        assertSanitizedError(
                request(tenantId).get("/v1/error-verification/unexpected"),
                500,
                "server-error");
    }

    private void assertRejectedCreationDoesNotPersist(
            UUID tenantId,
            String idempotencyKey,
            String body,
            int expectedStatus,
            String expectedCode) {
        long partiesBefore = countParties(tenantId);
        assertError(create(tenantId, idempotencyKey, body), expectedStatus, expectedCode);
        assertEquals(partiesBefore, countParties(tenantId));
        assertEquals(0, countIdempotencyRecords(tenantId, idempotencyKey));
    }

    private Map<String, Object> getData(UUID tenantId, String partyId) {
        return assertSuccess(request(tenantId).get(RESOURCE_PATH + "/" + partyId), 200);
    }

    private long countParties(UUID tenantId) {
        return awaitReactive(() -> sessionFactory.withSession(session -> session.createQuery("""
                select count(party)
                from PartyEntity party
                where party.tenantId = :tenantId
                """, Long.class)
                .setParameter("tenantId", tenantId)
                .getSingleResult()));
    }

    private long countDetails(UUID partyId) {
        return awaitReactive(() -> sessionFactory.withSession(session -> session.createQuery("""
                select count(details)
                from NaturalPersonDetailsEntity details
                where details.partyId = :partyId
                """, Long.class)
                .setParameter("partyId", partyId)
                .getSingleResult()));
    }

    private long countIdempotencyRecords(UUID tenantId, String idempotencyKey) {
        return awaitReactive(() -> sessionFactory.withSession(session -> session.createQuery("""
                select count(record)
                from ApiIdempotencyRecordEntity record
                where record.id.tenantId = :tenantId
                  and record.id.operation = :operation
                  and record.id.idempotencyKey = :idempotencyKey
                """, Long.class)
                .setParameter("tenantId", tenantId)
                .setParameter("operation", CreateNaturalPersonCommand.OPERATION)
                .setParameter("idempotencyKey", idempotencyKey)
                .getSingleResult()));
    }

    private void persistLegalEntity(UUID tenantId, UUID partyId) {
        awaitReactive(() -> sessionFactory.withTransaction((session, transaction) -> session
                .createNativeQuery("""
                        insert into parties (
                            id,
                            tenant_id,
                            type,
                            display_name,
                            created_by,
                            updated_by
                        ) values (
                            :partyId,
                            :tenantId,
                            'LEGAL_ENTITY',
                            'Concealed Legal Entity',
                            :userId,
                            :userId
                        )
                        """)
                .setParameter("partyId", partyId)
                .setParameter("tenantId", tenantId)
                .setParameter("userId", USER_ID)
                .executeUpdate()
                .chain(() -> session.createNativeQuery("""
                        insert into legal_entity_details (
                            party_id,
                            legal_name,
                            incorporation_country_code,
                            created_by,
                            updated_by
                        ) values (
                            :partyId,
                            'Concealed Legal Entity',
                            'EC',
                            :userId,
                            :userId
                        )
                        """)
                        .setParameter("partyId", partyId)
                        .setParameter("userId", USER_ID)
                        .executeUpdate())
                .replaceWithVoid()));
    }

    private <T> T awaitReactive(Supplier<Uni<T>> operation) {
        try {
            return VertxContextSupport.subscribeAndAwait(
                    () -> operation.get().ifNoItem().after(MAXIMUM_WAIT).fail());
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new AssertionError("Reactive test operation failed", failure);
        }
    }

    private static Response create(UUID tenantId, String idempotencyKey, String body) {
        return request(tenantId)
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .body(body)
                .post(RESOURCE_PATH);
    }

    private static Response put(
            UUID tenantId,
            String partyId,
            String expectedVersion,
            String body) {
        return request(tenantId)
                .header(IF_MATCH_HEADER, expectedVersion)
                .body(body)
                .put(RESOURCE_PATH + "/" + partyId);
    }

    private static Response patch(
            UUID tenantId,
            String partyId,
            String expectedVersion,
            String body) {
        return request(tenantId)
                .header(IF_MATCH_HEADER, expectedVersion)
                .body(body)
                .patch(RESOURCE_PATH + "/" + partyId);
    }

    private static RequestSpecification request(UUID tenantId) {
        return given()
                .contentType(ContentType.JSON)
                .header(RequestContextFilter.TENANT_ID_HEADER, tenantId.toString())
                .header(RequestContextFilter.USER_ID_HEADER, USER_ID)
                .header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS_ID);
    }

    private static Map<String, Object> assertSuccess(Response response, int expectedStatus) {
        assertEquals(expectedStatus, response.statusCode());
        assertEquals(PROCESS_ID, response.header(RequestContextFilter.PROCESS_ID_HEADER));
        Map<String, Object> envelope = response.jsonPath().getMap("$");
        assertEquals(SUCCESS_ENVELOPE_FIELDS, envelope.keySet());
        assertEquals(expectedStatus, number(envelope, "status"));
        assertEquals("successful", envelope.get("code"));
        Map<String, Object> data = response.jsonPath().getMap("data");
        assertNotNull(data);
        return data;
    }

    private static Map<String, Object> assertError(
            Response response,
            int expectedStatus,
            String expectedCode) {
        assertEquals(expectedStatus, response.statusCode());
        assertEquals(PROCESS_ID, response.header(RequestContextFilter.PROCESS_ID_HEADER));
        Map<String, Object> envelope = response.jsonPath().getMap("$");
        assertEquals(ERROR_ENVELOPE_FIELDS, envelope.keySet());
        assertEquals(expectedStatus, number(envelope, "status"));
        assertEquals(expectedCode, envelope.get("code"));
        assertFalse(envelope.containsKey("data"));
        assertNull(response.jsonPath().get("data"));
        return envelope;
    }

    private static void assertSanitizedError(
            Response response,
            int expectedStatus,
            String expectedCode) {
        assertError(response, expectedStatus, expectedCode);
        String body = response.asString().toLowerCase();
        for (String forbidden : List.of(
                "exception",
                "stack",
                "sql",
                "constraint",
                "hibernate",
                "domain.model",
                "persistence",
                "sensitive-database-detail")) {
            assertFalse(body.contains(forbidden), () -> "Response leaked internal detail: " + forbidden);
        }
    }

    private static List<Response> race(
            Supplier<Response> firstRequest,
            Supplier<Response> secondRequest) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Response> first = executor.submit(() -> executeWhenReleased(ready, start, firstRequest));
            Future<Response> second = executor.submit(() -> executeWhenReleased(ready, start, secondRequest));
            assertTrue(ready.await(MAXIMUM_WAIT.toMillis(), TimeUnit.MILLISECONDS));
            start.countDown();
            return List.of(
                    first.get(MAXIMUM_WAIT.toMillis(), TimeUnit.MILLISECONDS),
                    second.get(MAXIMUM_WAIT.toMillis(), TimeUnit.MILLISECONDS));
        }
    }

    private static Response executeWhenReleased(
            CountDownLatch ready,
            CountDownLatch start,
            Supplier<Response> request) throws InterruptedException {
        ready.countDown();
        if (!start.await(MAXIMUM_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new AssertionError("Concurrent HTTP requests were not released");
        }
        return request.get();
    }

    private static String createBody(
            String givenNames,
            String familyNames,
            String birthCountryCode) {
        String country = birthCountryCode == null
                ? ""
                : ",\"birthCountryCode\":\"" + birthCountryCode + "\"";
        return "{\"givenNames\":\"" + givenNames
                + "\",\"familyNames\":\"" + familyNames + "\"" + country + "}";
    }

    private static String key(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static String string(Map<String, Object> values, String key) {
        return (String) values.get(key);
    }

    private static int number(Map<String, Object> values, String key) {
        return ((Number) values.get(key)).intValue();
    }

    private static void assertEquivalentData(
            Map<String, Object> expected,
            Map<String, Object> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            if (entry.getKey().equals("createdAt") || entry.getKey().equals("updatedAt")) {
                assertTimestampEquivalent(entry.getValue(), actual.get(entry.getKey()));
            } else {
                assertEquals(entry.getValue(), actual.get(entry.getKey()));
            }
        }
    }

    private static void assertTimestampEquivalent(Object expected, Object actual) {
        assertEquals(
                Instant.parse((String) expected).truncatedTo(ChronoUnit.MICROS),
                Instant.parse((String) actual).truncatedTo(ChronoUnit.MICROS));
    }

    private static Map<String, Object> nested(Map<String, Object> values, String key) {
        Object nested = values.get(key);
        assertTrue(nested instanceof Map<?, ?>);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) nested).entrySet()) {
            assertTrue(entry.getKey() instanceof String);
            result.put((String) entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Enables the controlled unexpected-failure probe while retaining real
     * persistence.
     */
    public static final class ContractProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("party-registry.error-verification.enabled", "true");
        }
    }
}
