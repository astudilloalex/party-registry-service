package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.api.filter.RequestContextFilter;
import com.alexastudillo.partyregistry.application.command.RegisterNaturalPersonCommand;
import io.quarkus.test.junit.QuarkusTest;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the complete natural-person HTTP contract against PostgreSQL and the
 * controlled Geographic Reference boundary.
 */
@QuarkusTest
class NaturalPersonResourceContractTest {

    private static final String TENANT_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String PROCESS_ID = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";
    private static final String USER_ID = "geographic-reference-adapter-test";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IF_MATCH_HEADER = "If-Match";
    private static final String RESOURCE_PATH = "/v1/natural-person";
    private static final String FAMILY_NAMES_REQUIRED = "family-names-required";
    private static final String PARTY_ID_INVALID = "party-id-invalid";
    private static final String IF_MATCH_REQUIRED = "if-match-required";
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
            "naturalPersonDetails",
            "initialIdentifier");
    private static final Set<String> COMPLETE_DETAILS_FIELDS = Set.of(
            "givenNames",
            "familyNames",
            "preferredName",
            "birthDate",
            "dateOfDeath",
            "birthCountryCode");
    private static final Set<String> COMPLETE_IDENTIFIER_FIELDS = Set.of(
            "identifierId",
            "partyId",
            "identifierSchemeId",
            "schemeCode",
            "maskedValue",
            "status",
            "isPrimary",
            "issuerCode",
            "issuedOn",
            "expiresOn",
            "verifiedAt",
            "verifiedBy",
            "version",
            "createdAt",
            "updatedAt");

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
        assertEquals("THE COUNTESS OF LOVELACE", explicit.get("displayName"));
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

        assertExplicitNaturalPersonDetails(explicit);
        assertInitialIdentifier(explicit);
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
        assertEquals("KATHERINE JOHNSON", derived.get("displayName"));
        Map<String, Object> derivedDetails = nested(derived, "naturalPersonDetails");
        assertEquals("KATHERINE", derivedDetails.get("givenNames"));
        assertEquals("JOHNSON", derivedDetails.get("familyNames"));
        assertEquals(COMPLETE_DETAILS_FIELDS, derivedDetails.keySet());
        assertNull(derivedDetails.get("preferredName"));
        assertNull(derivedDetails.get("birthDate"));
        assertNull(derivedDetails.get("dateOfDeath"));
        assertNull(derivedDetails.get("birthCountryCode"));
    }

    @Test
    void normalizesFieldsOnCreateAndRejectsRawRequestVariationsWithIdempotencyConflict() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        String idempotencyKey = key("normalized-create");
        String body = """
                {
                  "displayName": "  the Countess  ",
                  "givenNames": "  aDa  augusta  ",
                  "familyNames": "\u2003loveLace\u2003",
                  "preferredName": "  countess  ",
                  "birthDate": "1815-12-10",
                  "dateOfDeath": "1852-11-27",
                  "birthCountryCode": "\u2003eC\u2003"
                }
                """;
        Map<String, Object> created = assertSuccess(create(tenantId, idempotencyKey, body), 201);
        String partyId = string(created, "partyId");
        assertEquals("THE COUNTESS", created.get("displayName"));
        assertEquals(Map.of(
                "givenNames", "ADA  AUGUSTA",
                "familyNames", "LOVELACE",
                "preferredName", "COUNTESS",
                "birthDate", "1815-12-10",
                "dateOfDeath", "1852-11-27",
                "birthCountryCode", "EC"), nested(created, "naturalPersonDetails"));
        assertStoredNaturalPerson(tenantId, created);
        assertEquivalentData(created, getData(tenantId, partyId));
        assertEquals(created, assertSuccess(create(tenantId, idempotencyKey, body), 201));

        for (String changedBody : List.of(
                body.replace("the Countess", "THE COUNTESS"),
                body.replace("aDa", "ADA"),
                body.replace("loveLace", "LOVELACE"),
                body.replace("countess", "COUNTESS"),
                body.replace("eC", "EC"),
                body.replace("  aDa  augusta  ", "aDa  augusta"))) {
            assertError(create(tenantId, idempotencyKey, changedBody), 409, "idempotency-key-conflict");
        }
        assertEquivalentData(created, getData(tenantId, partyId));
        assertStoredNaturalPerson(tenantId, created);
    }

    @Test
    void normalizesFieldsOnUpdateWithoutChangingRawRequestReplay() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        String idempotencyKey = key("normalized-updates");
        String createBody = """
                {
                  "displayName": "  the Countess  ",
                  "givenNames": "  aDa  augusta  ",
                  "familyNames": "\u2003loveLace\u2003",
                  "preferredName": "  countess  ",
                  "birthDate": "1815-12-10",
                  "dateOfDeath": "1852-11-27",
                  "birthCountryCode": "\u2003eC\u2003"
                }
                """;
        Map<String, Object> created = assertSuccess(create(tenantId, idempotencyKey, createBody), 201);
        String partyId = string(created, "partyId");

        Map<String, Object> replaced = assertSuccess(put(tenantId, partyId, "0", """
                {
                  "givenNames": "  gRaCe  ",
                  "familyNames": "\u2003hoPPer\u2003",
                  "preferredName": "  amazing grace  ",
                  "birthDate": "1906-12-09",
                  "dateOfDeath": "1992-01-01",
                  "birthCountryCode": "  ec  "
                }
                """), 200);
        assertEquals("GRACE HOPPER", replaced.get("displayName"));
        assertEquals(1, number(replaced, "version"));
        assertEquals(Map.of(
                "givenNames", "GRACE",
                "familyNames", "HOPPER",
                "preferredName", "AMAZING GRACE",
                "birthDate", "1906-12-09",
                "dateOfDeath", "1992-01-01",
                "birthCountryCode", "EC"), nested(replaced, "naturalPersonDetails"));
        assertStoredNaturalPerson(tenantId, replaced);
        assertEquivalentData(replaced, getData(tenantId, partyId));

        Map<String, Object> patched = assertSuccess(patch(tenantId, partyId, "1", """
                {
                  "givenNames": "\u2003kaTherine\u2003",
                  "familyNames": "  joHnSon  ",
                  "preferredName": "\u2003kathy\u2003",
                  "birthDate": "1918-08-26",
                  "dateOfDeath": "2020-02-24",
                  "birthCountryCode": "  Ec  "
                }
                """), 200);
        assertEquals("KATHERINE JOHNSON", patched.get("displayName"));
        assertEquals(2, number(patched, "version"));
        assertEquals(Map.of(
                "givenNames", "KATHERINE",
                "familyNames", "JOHNSON",
                "preferredName", "KATHY",
                "birthDate", "1918-08-26",
                "dateOfDeath", "2020-02-24",
                "birthCountryCode", "EC"), nested(patched, "naturalPersonDetails"));
        assertStoredNaturalPerson(tenantId, patched);

        assertEquals(created, assertSuccess(create(tenantId, idempotencyKey, createBody), 201));
        assertEquivalentData(patched, getData(tenantId, partyId));
        assertEquals(1, countDetails(UUID.fromString(partyId)));
        assertEquals(1, countIdempotencyRecords(tenantId, idempotencyKey));
    }

    @Test
    void preservesLegacyNamesOnReadAndOmittedPatchFieldsWhileClearingExplicitNulls() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        Map<String, Object> created = assertSuccess(
                create(tenantId, key("legacy-names"), COMPLETE_CREATE_BODY), 201);
        String partyId = string(created, "partyId");

        // Model rows written before normalization without rewriting their creation snapshot.
        awaitReactive(() -> sessionFactory.withTransaction((session, transaction) -> session
                .createNativeQuery("""
                        update parties set display_name = '  Legacy Display  '
                        where id = :partyId and tenant_id = :tenantId
                        """)
                .setParameter("partyId", UUID.fromString(partyId))
                .setParameter("tenantId", tenantId)
                .executeUpdate()
                .invoke(updated -> assertEquals(1, updated))
                .chain(() -> session.createNativeQuery("""
                        update natural_person_details
                        set given_names = '  Legacy Given  ', family_names = 'Legacy Family',
                            preferred_name = ' Legacy Preference '
                        where party_id = :partyId
                        """)
                        .setParameter("partyId", UUID.fromString(partyId))
                        .executeUpdate()
                        .invoke(updated -> assertEquals(1, updated)))
                .replaceWithVoid()));

        Map<String, Object> restored = getData(tenantId, partyId);
        assertEquals("  Legacy Display  ", restored.get("displayName"));
        Map<String, Object> expectedDetails = new LinkedHashMap<>(Map.of(
                "givenNames", "  Legacy Given  ",
                "familyNames", "Legacy Family",
                "preferredName", " Legacy Preference ",
                "birthDate", "1815-12-10",
                "dateOfDeath", "1852-11-27",
                "birthCountryCode", "EC"));
        assertEquals(expectedDetails, nested(restored, "naturalPersonDetails"));
        assertStoredNaturalPerson(tenantId, restored);

        Map<String, Object> patched = assertSuccess(
                patch(tenantId, partyId, "0", "{\"preferredName\":\"  new Preference  \"}"), 200);
        expectedDetails.put("preferredName", "NEW PREFERENCE");
        assertEquals(expectedDetails, nested(patched, "naturalPersonDetails"));
        assertEquals(restored.get("displayName"), patched.get("displayName"));
        assertEquals(1, number(patched, "version"));
        assertStoredNaturalPerson(tenantId, patched);

        Map<String, Object> cleared = assertSuccess(patch(tenantId, partyId, "1", """
                {"preferredName":null,"birthDate":null,"dateOfDeath":null,"birthCountryCode":null}
                """), 200);
        for (String field : List.of("preferredName", "birthDate", "dateOfDeath", "birthCountryCode")) {
            expectedDetails.put(field, null);
        }
        assertEquals(expectedDetails, nested(cleared, "naturalPersonDetails"));
        assertEquals(restored.get("displayName"), cleared.get("displayName"));
        assertEquals(2, number(cleared, "version"));
        assertStoredNaturalPerson(tenantId, cleared);
        assertEquivalentData(cleared, getData(tenantId, partyId));
    }

    @Test
    void rejectsNonAsciiAndEmbeddedWhitespaceCountriesOnEveryWriteWithoutChangingStoredData() {
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> created = assertSuccess(
                create(tenantId, key("country-format"), createBody("Country", "Format", null)), 201);
        String partyId = string(created, "partyId");
        for (String invalidCountry : List.of("e c", "\u00df", "\u017fs", "\u00e9c")) {
            assertRejectedCreationDoesNotPersist(tenantId, key("invalid-country"),
                    createBody("Country", "Format", invalidCountry), 400, "birth-country-code-invalid");
            assertError(put(tenantId, partyId, "0", createBody("Country", "Format", invalidCountry)),
                    400, "birth-country-code-invalid");
            assertError(patch(tenantId, partyId, "0", "{\"birthCountryCode\":\"" + invalidCountry + "\"}"),
                    400, "birth-country-code-invalid");
        }
        assertEquivalentData(created, getData(tenantId, partyId));
        assertStoredNaturalPerson(tenantId, created);
    }

    @Test
    void verifiesCreationValidationCountryOutcomesAndAtomicRejection() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        UUID validationTenant = UUID.randomUUID();

        String missingIdentifierKey = key("missing-identifier");
        long partiesBeforeMissingIdentifier = countParties(validationTenant);
        assertError(
                request(validationTenant)
                        .header(IDEMPOTENCY_KEY_HEADER, missingIdentifierKey)
                        .body("{\"givenNames\":\"Ada\",\"familyNames\":\"Lovelace\"}")
                        .post(RESOURCE_PATH),
                400,
                "initial-identifier-required");
        assertEquals(partiesBeforeMissingIdentifier, countParties(validationTenant));
        assertEquals(0, countIdempotencyRecords(validationTenant, missingIdentifierKey));

        assertRejectedCreationDoesNotPersist(
                validationTenant,
                key("missing-name"),
                "{\"givenNames\":\"Ada\"}",
                400,
                FAMILY_NAMES_REQUIRED);
        assertRejectedCreationDoesNotPersist(
                validationTenant,
                key("invalid-country-format"),
                "{\"givenNames\":\"Ada\",\"familyNames\":\"Lovelace\",\"birthCountryCode\":\"ecu\"}",
                400,
                "birth-country-code-invalid");
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
        String body = withInitialIdentifier(createBody("Idempotent", "Person", null), "header-validation");
        long initialRows = countParties(invalidTenant);

        assertError(request(invalidTenant).body(body).post(RESOURCE_PATH), 400, "idempotency-key-required");
        assertError(
                request(invalidTenant)
                        .header(IDEMPOTENCY_KEY_HEADER, "duplicate", "duplicate")
                        .body(body)
                        .post(RESOURCE_PATH),
                400,
                "idempotency-key-duplicated");
        assertError(create(invalidTenant, "   ", body), 400, "idempotency-key-blank");
        assertError(create(invalidTenant, "x".repeat(129), body), 400, "idempotency-key-too-long");
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
        assertEquals(1, countIdentifiers(replayTenant));
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
        assertEquals(1, countIdentifiers(concurrentTenant));
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
        assertError(request(tenantId).get(RESOURCE_PATH + "/not-a-uuid"), 400, PARTY_ID_INVALID);

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
    void retrievesInitialAndAdditionalCurrentIdentifiersWithoutDecryptingOrFilteringInactiveSchemes() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        String createKey = key("current-identifiers");
        Map<String, Object> created = assertSuccess(create(tenantId, createKey, COMPLETE_CREATE_BODY), 201);
        String partyId = string(created, "partyId");
        String additionalValue = "  detail" + UUID.randomUUID().toString().substring(0, 8) + "  ";
        Map<String, Object> additional = assertSuccess(request(tenantId)
                .header(IDEMPOTENCY_KEY_HEADER, key("current-additional"))
                .body("""
                        {
                          "identifierSchemeCode":"TEST_BOTH_EXPIRING",
                          "value":"%s",
                          "issuerCode":"REGISTRY",
                          "issuedOn":"2000-01-01",
                          "isPrimary":false
                        }
                        """.formatted(additionalValue))
                .post("/v1/parties/{partyId}/identifiers", partyId), 201);
        List<Map<String, Object>> excluded = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            excluded.add(assertSuccess(request(tenantId)
                    .header(IDEMPOTENCY_KEY_HEADER, key("excluded-identifier"))
                    .body("""
                            {"identifierSchemeCode":"TEST_NATURAL_ACTIVE","value":"%s"}
                            """.formatted(identifierValue(key("excluded"))))
                    .post("/v1/parties/{partyId}/identifiers", partyId), 201));
        }

        LocalDate evaluationDate = LocalDate.now(ZoneOffset.UTC);
        setIdentifierLifecycle(tenantId, additional, "VERIFIED", evaluationDate);
        setIdentifierLifecycle(tenantId, excluded.get(0), "PENDING_VERIFICATION", evaluationDate.minusDays(1));
        setIdentifierLifecycle(tenantId, excluded.get(1), "VERIFIED", evaluationDate.minusDays(1));
        setIdentifierLifecycle(tenantId, excluded.get(2), "EXPIRED", evaluationDate.plusDays(1));
        setIdentifierLifecycle(tenantId, excluded.get(3), "REJECTED", evaluationDate.plusDays(1));
        setIdentifierLifecycle(tenantId, excluded.get(4), "REVOKED", evaluationDate.plusDays(1));

        // Model a historical retired scheme and an unavailable key without mutating shared scheme fixtures.
        awaitReactive(() -> sessionFactory.withTransaction((session, transaction) -> session.createNativeQuery("""
                update party_identifiers
                set identifier_scheme_id = '0198d111-08f1-7e48-b291-399bbb9cd606',
                    encrypted_value = 'detail-ciphertext-must-not-be-decrypted', encryption_key_version = 32767
                where tenant_id = :tenantId and id = :identifierId
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("identifierId", UUID.fromString(string(additional, "identifierId")))
                .executeUpdate().invoke(updated -> assertEquals(1, updated))));

        Response response = request(tenantId).get(RESOURCE_PATH + "/" + partyId);
        Map<String, Object> detail = assertDetailSuccess(response);
        assertExplicitNaturalPersonDetails(detail);
        List<Map<String, Object>> current = identifiers(detail);
        assertEquals(2, current.size());
        Map<String, Object> expectedAdditional = new LinkedHashMap<>(additional);
        expectedAdditional.put("identifierSchemeId", "0198d111-08f1-7e48-b291-399bbb9cd606");
        expectedAdditional.put("schemeCode", "TEST_BOTH_RETIRED");
        expectedAdditional.put("status", "VERIFIED");
        expectedAdditional.put("expiresOn", evaluationDate.toString());
        expectedAdditional.put("verifiedAt", "2020-01-02T00:00:00Z");
        expectedAdditional.put("verifiedBy", USER_ID);
        expectedAdditional.put("version", 1);
        List<Map<String, Object>> expected = List.of(nested(created, "initialIdentifier"), expectedAdditional);
        for (int index = 0; index < expected.size(); index++) {
            for (var field : expected.get(index).entrySet()) {
                if (Set.of("createdAt", "updatedAt", "verifiedAt").contains(field.getKey())
                        && field.getValue() != null) {
                    assertTimestampEquivalent(field.getValue(), current.get(index).get(field.getKey()));
                } else {
                    assertEquals(field.getValue(), current.get(index).get(field.getKey()), field.getKey());
                }
            }
        }
        Map<String, Object> base = new LinkedHashMap<>(detail);
        base.remove("identifiers");
        assertEquivalentData(created, base);
        for (String plaintext : List.of(identifierValue(createKey), identifierValue(createKey).toUpperCase(Locale.ROOT),
                additionalValue, additionalValue.strip(), additionalValue.strip().toUpperCase(Locale.ROOT))) {
            assertFalse(response.asString().contains(plaintext));
        }
        List<Object[]> protectedValues = awaitReactive(() -> sessionFactory.withSession(session -> session
                .createNativeQuery("""
                        select encrypted_value, normalized_value_hash from party_identifiers
                        where tenant_id = :tenantId and party_id = :partyId
                        """, Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("partyId", UUID.fromString(partyId))
                .getResultList()));
        for (Object[] protectedValue : protectedValues) {
            assertFalse(response.asString().contains((String) protectedValue[0]));
            assertFalse(response.asString().contains(((String) protectedValue[1]).strip()));
        }
        assertError(request(UUID.randomUUID()).get(RESOURCE_PATH + "/" + partyId), 404, "natural-person-not-found");
    }

    @Test
    void retrievesMoreThanFiftyIdentifiersInCreationAndIdentifierIdOrder() {
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> created = assertSuccess(
                create(tenantId, key("complete-collection"), createBody("Complete", "Collection", null)), 201);
        String partyId = string(created, "partyId");
        String idPrefix = UUID.randomUUID().toString().substring(0, 24);

        // Insert tied timestamps and reverse UUID order to distinguish both sort keys from insertion order.
        awaitReactive(() -> sessionFactory.withTransaction((session, transaction) -> session.createNativeQuery("""
                insert into party_identifiers (
                    id, tenant_id, party_id, identifier_scheme_id, encrypted_value, encryption_key_version,
                    normalized_value_hash, masked_value, created_at, created_by, updated_by
                )
                select cast(:idPrefix || lpad(cast(56 - ordinal as text), 12, '0') as uuid),
                    :tenantId, :partyId, '0198d111-08f1-7e48-b291-399bbb9cd605',
                    'detail-undecipherable-fixture', 32767,
                    md5(:idPrefix || ordinal) || md5(:idPrefix || ordinal), '****' || ordinal,
                    timestamptz '2000-01-01 00:00:00+00' + (ordinal % 3) * interval '1 second',
                    :userId, :userId
                from generate_series(1, 55) as fixture(ordinal)
                """)
                .setParameter("idPrefix", idPrefix)
                .setParameter("tenantId", tenantId)
                .setParameter("partyId", UUID.fromString(partyId))
                .setParameter("userId", USER_ID)
                .executeUpdate().invoke(inserted -> assertEquals(55, inserted))));

        Map<String, Object> detail = assertDetailSuccess(request(tenantId).get(RESOURCE_PATH + "/" + partyId));
        List<Map<String, Object>> current = identifiers(detail);
        assertEquals(56, current.size());
        List<String> expectedIds = new ArrayList<>();
        for (int group = 0; group < 3; group++) {
            for (int ordinal = 55; ordinal >= 1; ordinal--) {
                if (ordinal % 3 == group) {
                    expectedIds.add(idPrefix + String.format(Locale.ROOT, "%012d", 56 - ordinal));
                }
            }
        }
        expectedIds.add(string(nested(created, "initialIdentifier"), "identifierId"));
        assertEquals(expectedIds, current.stream().map(identifier -> string(identifier, "identifierId")).toList());
        assertEquals(55, current.stream().filter(identifier -> "TEST_BOTH_DEPRECATED".equals(identifier.get("schemeCode")))
                .count());
    }

    @Test
    void returnsAnEmptyIdentifierArrayForLegacyPartiesAndPartiesWithoutCurrentIdentifiers() {
        UUID tenantId = UUID.randomUUID();
        UUID legacyPartyId = UUID.randomUUID();
        awaitReactive(() -> sessionFactory.withTransaction((session, transaction) -> session.createNativeQuery("""
                insert into parties (id, tenant_id, type, display_name, created_by, updated_by)
                values (:partyId, :tenantId, 'NATURAL_PERSON', 'Legacy Person', :userId, :userId)
                """)
                .setParameter("partyId", legacyPartyId)
                .setParameter("tenantId", tenantId)
                .setParameter("userId", USER_ID)
                .executeUpdate().invoke(inserted -> assertEquals(1, inserted))
                .chain(() -> session.createNativeQuery("""
                        insert into natural_person_details (party_id, given_names, family_names, created_by, updated_by)
                        values (:partyId, 'Legacy', 'Person', :userId, :userId)
                        """)
                        .setParameter("partyId", legacyPartyId)
                        .setParameter("userId", USER_ID)
                        .executeUpdate().invoke(inserted -> assertEquals(1, inserted)))));
        Map<String, Object> legacy = assertDetailSuccess(request(tenantId).get(RESOURCE_PATH + "/" + legacyPartyId));
        assertEquals(List.of(), identifiers(legacy));
        assertEquals("Legacy Person", legacy.get("displayName"));
        assertEquals("Legacy", nested(legacy, "naturalPersonDetails").get("givenNames"));
        assertError(request(UUID.randomUUID()).get(RESOURCE_PATH + "/" + legacyPartyId), 404, "natural-person-not-found");

        Map<String, Object> created = assertSuccess(
                create(tenantId, key("no-current-identifiers"), createBody("No", "Current Identifiers", null)), 201);
        setIdentifierLifecycle(tenantId, nested(created, "initialIdentifier"), "REVOKED", null);
        Map<String, Object> detail = assertDetailSuccess(
                request(tenantId).get(RESOURCE_PATH + "/" + string(created, "partyId")));
        assertEquals(List.of(), identifiers(detail));
        assertEquivalentData(created, getData(tenantId, string(created, "partyId")));
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
        String replacementBody = "{\"givenNames\":\"Grace\",\"familyNames\":\"Hopper\"}";

        assertError(
                put(tenantId, partyId, "0", "{\"givenNames\":\"Grace\"}"),
                400,
                FAMILY_NAMES_REQUIRED);
        assertError(
                request(tenantId)
                        .body(replacementBody)
                        .put(RESOURCE_PATH + "/" + partyId),
                400,
                IF_MATCH_REQUIRED);
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
                        .body(replacementBody)
                        .put(RESOURCE_PATH + "/" + partyId),
                400,
                "if-match-duplicated");
        for (String malformedVersion : List.of("-1", "01", "1.0")) {
            assertError(
                    put(
                            tenantId,
                            partyId,
                            malformedVersion,
                            replacementBody),
                    400,
                    "if-match-invalid");
        }
        assertError(
                put(tenantId, partyId, "9223372036854775808", replacementBody),
                400,
                "if-match-out-of-range");
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
        assertEquals("GRACE HOPPER", replaced.get("displayName"));
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
                IF_MATCH_REQUIRED);

        Map<String, Object> preferred = assertSuccess(
                patch(tenantId, partyId, "0", "{\"preferredName\":\"Kathy\"}"),
                200);
        assertEquals(1, number(preferred, "version"));
        assertEquals("NASA MATHEMATICIAN", preferred.get("displayName"));
        Map<String, Object> preferredDetails = nested(preferred, "naturalPersonDetails");
        assertEquals("KATHY", preferredDetails.get("preferredName"));
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
        assertEquals("KATHERINE GOBBLE JOHNSON", renamed.get("displayName"));
        assertEquals("KATHERINE", nested(renamed, "naturalPersonDetails").get("givenNames"));

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

        assertError(patch(tenantId, partyId, "0", "{}"), 400, "patch-property-required");
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
            assertTrue(persistedPreference.equals(firstPreference.toUpperCase(Locale.ROOT))
                    || persistedPreference.equals(secondPreference.toUpperCase(Locale.ROOT)));
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
        assertDetailSuccess(request(tenantId).get(RESOURCE_PATH + "/" + partyId));
        assertSanitizedError(
                request(tenantId).get(RESOURCE_PATH + "/invalid-id"),
                400,
                PARTY_ID_INVALID);
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
        Map<String, Object> data = new LinkedHashMap<>(
                assertDetailSuccess(request(tenantId).get(RESOURCE_PATH + "/" + partyId)));
        // Preserve the existing write/read comparisons after validating the GET-only collection separately.
        data.remove("identifiers");
        return data;
    }

    private void setIdentifierLifecycle(
            UUID tenantId, Map<String, Object> identifier, String status, LocalDate expiresOn) {
        awaitReactive(() -> sessionFactory.withTransaction((session, transaction) -> session.createNativeQuery("""
                update party_identifiers
                set status = cast(:status as party_identifier_status), expires_on = :expiresOn,
                    verified_at = case when :status = 'VERIFIED' then timestamptz '2020-01-02 00:00:00+00' end,
                    verified_by = case when :status = 'VERIFIED' then :userId end,
                    version = version + 1
                where tenant_id = :tenantId and id = :identifierId
                """)
                .setParameter("status", status)
                .setParameter("expiresOn", expiresOn)
                .setParameter("userId", USER_ID)
                .setParameter("tenantId", tenantId)
                .setParameter("identifierId", UUID.fromString(string(identifier, "identifierId")))
                .executeUpdate().invoke(updated -> assertEquals(1, updated))));
    }

    private void assertStoredNaturalPerson(UUID tenantId, Map<String, Object> expected) {
        Object[] stored = awaitReactive(() -> sessionFactory.withSession(session -> session.createQuery("""
                select party.displayName, details.givenNames, details.familyNames, details.preferredName,
                       details.birthDate, details.dateOfDeath, details.birthCountryCode
                from NaturalPersonDetailsEntity details join details.party party
                where party.tenantId = :tenantId and party.id = :partyId
                """, Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("partyId", UUID.fromString(string(expected, "partyId")))
                .getSingleResult()));
        assertEquals(expected.get("displayName"), stored[0]);
        Map<String, Object> details = nested(expected, "naturalPersonDetails");
        List<String> fields = List.of(
                "givenNames", "familyNames", "preferredName", "birthDate", "dateOfDeath", "birthCountryCode");
        for (int index = 0; index < fields.size(); index++) {
            Object value = stored[index + 1];
            assertEquals(details.get(fields.get(index)), value == null ? null : value.toString(), fields.get(index));
        }
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
                .setParameter("operation", RegisterNaturalPersonCommand.OPERATION_NAME)
                .setParameter("idempotencyKey", idempotencyKey)
                .getSingleResult()));
    }

    private long countIdentifiers(UUID tenantId) {
        return awaitReactive(() -> sessionFactory.withSession(session -> session.createQuery("""
                select count(identifier)
                from PartyIdentifierEntity identifier
                where identifier.tenantId = :tenantId
                """, Long.class)
                .setParameter("tenantId", tenantId)
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
                .body(withInitialIdentifier(body, idempotencyKey))
                .post(RESOURCE_PATH);
    }

    private static String withInitialIdentifier(String body, String idempotencyKey) {
        int closingBrace = body.lastIndexOf('}');
        if (closingBrace < 0 || body.contains("\"initialIdentifier\"")) {
            return body;
        }
        String prefix = body.substring(0, closingBrace);
        String separator = prefix.stripTrailing().endsWith("{") ? "" : ",";
        return prefix + separator + """
                "initialIdentifier":{
                  "identifierSchemeCode":"TEST_NATURAL_ACTIVE",
                  "value":"%s",
                  "isPrimary":true
                }}
                """.formatted(identifierValue(idempotencyKey));
    }

    private static String identifierValue(String idempotencyKey) {
        String alphanumeric = idempotencyKey.replaceAll("[^A-Za-z0-9]", "");
        if (alphanumeric.length() < 6) {
            return "ABC123";
        }
        return alphanumeric.substring(Math.max(0, alphanumeric.length() - 18));
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

    private static Map<String, Object> assertDetailSuccess(Response response) {
        Map<String, Object> data = assertSuccess(response, 200);
        Set<String> expectedFields = new HashSet<>(COMPLETE_DATA_FIELDS);
        expectedFields.remove("initialIdentifier");
        expectedFields.add("identifiers");
        assertEquals(expectedFields, data.keySet());
        assertEquals(COMPLETE_DETAILS_FIELDS, nested(data, "naturalPersonDetails").keySet());
        for (Map<String, Object> identifier : identifiers(data)) {
            assertEquals(COMPLETE_IDENTIFIER_FIELDS, identifier.keySet());
            assertEquals(data.get("partyId"), identifier.get("partyId"));
            assertTrue(Set.of("PENDING_VERIFICATION", "VERIFIED").contains(identifier.get("status")));
            UUID.fromString(string(identifier, "identifierId"));
            UUID.fromString(string(identifier, "identifierSchemeId"));
            assertFalse(string(identifier, "schemeCode").isBlank());
            assertFalse(string(identifier, "maskedValue").isBlank());
            assertInstanceOf(Boolean.class, identifier.get("isPrimary"));
            assertTrue(number(identifier, "version") >= 0);
            Instant.parse(string(identifier, "createdAt"));
            Instant.parse(string(identifier, "updatedAt"));
        }
        return data;
    }

    private static List<Map<String, Object>> identifiers(Map<String, Object> data) {
        List<?> values = assertInstanceOf(List.class, data.get("identifiers"));
        return values.stream().map(value -> nested(Map.of("identifier", value), "identifier")).toList();
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
        Map<String, Object> ordinaryExpected = new LinkedHashMap<>(expected);
        ordinaryExpected.remove("initialIdentifier");
        assertEquals(ordinaryExpected.keySet(), actual.keySet());
        for (Map.Entry<String, Object> entry : ordinaryExpected.entrySet()) {
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

    private static void assertExplicitNaturalPersonDetails(Map<String, Object> explicit) {
        Map<String, Object> explicitDetails = nested(explicit, "naturalPersonDetails");
        assertEquals("ADA", explicitDetails.get("givenNames"));
        assertEquals("LOVELACE", explicitDetails.get("familyNames"));
        assertEquals("ADA", explicitDetails.get("preferredName"));
        assertEquals("1815-12-10", explicitDetails.get("birthDate"));
        assertEquals("1852-11-27", explicitDetails.get("dateOfDeath"));
        assertEquals("EC", explicitDetails.get("birthCountryCode"));
    }

    private static void assertInitialIdentifier(Map<String, Object> explicit) {
        Map<String, Object> identifier = nested(explicit, "initialIdentifier");
        assertEquals(COMPLETE_IDENTIFIER_FIELDS, identifier.keySet());
        assertEquals(explicit.get("partyId"), identifier.get("partyId"));
        assertEquals("TEST_NATURAL_ACTIVE", identifier.get("schemeCode"));
        assertEquals("PENDING_VERIFICATION", identifier.get("status"));
        assertEquals(true, identifier.get("isPrimary"));
        assertFalse(identifier.containsKey("value"));
        assertFalse(identifier.containsKey("encryptedValue"));
        assertFalse(identifier.containsKey("normalizedValueHash"));
    }

}
