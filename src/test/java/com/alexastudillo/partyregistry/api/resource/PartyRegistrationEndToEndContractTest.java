package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.api.filter.RequestContextFilter;
import com.alexastudillo.partyregistry.application.command.RegisterNaturalPersonCommand;
import com.alexastudillo.partyregistry.support.StoredOutboxTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.vertx.VertxContextSupport;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies complete registration and confidentiality behavior through HTTP and PostgreSQL.
 */
@QuarkusTest
@TestProfile(StoredOutboxTestProfile.class)
@Timeout(90)
class PartyRegistrationEndToEndContractTest {

    private static final String TENANT_ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String PROCESS_ID = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";
    private static final String USER_ID = "geographic-reference-adapter-test";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final Duration MAXIMUM_WAIT = Duration.ofSeconds(10);
    private static final Set<String> SUCCESS_ENVELOPE_FIELDS = Set.of("status", "code", "data");
    private static final Set<String> ERROR_ENVELOPE_FIELDS = Set.of("status", "code");
    private static final Set<String> SAFE_IDENTIFIER_FIELDS = Set.of(
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

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    void rejectsStructuralSchemeAndSemanticFailuresWithoutAnyRegistrationRows() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        List<RejectedRegistration> registrations = List.of(
                new RejectedRegistration(
                        "/v1/natural-person",
                        "{\"givenNames\":\"Missing\",\"familyNames\":\"Identifier\"}",
                        400),
                new RejectedRegistration(
                        "/v1/natural-person",
                        naturalBody("Blank Scheme", " ", "ABC123", null, null),
                        400),
                new RejectedRegistration(
                        "/v1/natural-person",
                        naturalBody("Blank Value", "TEST_NATURAL_ACTIVE", " ", null, null),
                        400),
                new RejectedRegistration(
                        "/v1/natural-person",
                        naturalBody("Unknown Scheme", "UNKNOWN_SCHEME", identifierValue("NU"), null, null),
                        422),
                new RejectedRegistration(
                        "/v1/natural-person",
                        naturalBody("Inactive Scheme", "TEST_BOTH_DRAFT", identifierValue("ND"), null, null),
                        422),
                new RejectedRegistration(
                        "/v1/natural-person",
                        naturalBody("Wrong Subject", "TEST_LEGAL_ACTIVE", identifierValue("NS"), null, null),
                        422),
                new RejectedRegistration(
                        "/v1/natural-person",
                        naturalBody("Invalid Value", "TEST_NATURAL_ACTIVE", "ABC-123", null, null),
                        422),
                new RejectedRegistration(
                        "/v1/natural-person",
                        naturalBody("Missing Expiry", "TEST_BOTH_EXPIRING", identifierValue("NE"), null, null),
                        422),
                new RejectedRegistration(
                        "/v1/natural-person",
                        naturalBody("Expired Value", "TEST_BOTH_EXPIRING", identifierValue("NX"), null, "2000-01-01"),
                        422),
                new RejectedRegistration(
                        "/v1/legal-entity",
                        "{\"legalName\":\"Missing Identifier Ltd\",\"incorporationCountryCode\":\"EC\"}",
                        400),
                new RejectedRegistration(
                        "/v1/legal-entity",
                        legalBody("Blank Scheme Ltd", " ", "ABC123", null, null),
                        400),
                new RejectedRegistration(
                        "/v1/legal-entity",
                        legalBody("Blank Value Ltd", "TEST_LEGAL_ACTIVE", " ", null, null),
                        400),
                new RejectedRegistration(
                        "/v1/legal-entity",
                        legalBody("Unknown Scheme Ltd", "UNKNOWN_SCHEME", identifierValue("LU"), null, null),
                        422),
                new RejectedRegistration(
                        "/v1/legal-entity",
                        legalBody("Inactive Scheme Ltd", "TEST_BOTH_DRAFT", identifierValue("LD"), null, null),
                        422),
                new RejectedRegistration(
                        "/v1/legal-entity",
                        legalBody("Wrong Subject Ltd", "TEST_NATURAL_ACTIVE", identifierValue("LS"), null, null),
                        422),
                new RejectedRegistration(
                        "/v1/legal-entity",
                        legalBody("Invalid Value Ltd", "TEST_LEGAL_ACTIVE", "ABC-123", null, null),
                        422),
                new RejectedRegistration(
                        "/v1/legal-entity",
                        legalBody("Missing Expiry Ltd", "TEST_BOTH_EXPIRING", identifierValue("LE"), null, null),
                        422),
                new RejectedRegistration(
                        "/v1/legal-entity",
                        legalBody("Expired Value Ltd", "TEST_BOTH_EXPIRING", identifierValue("LX"), null, "2000-01-01"),
                        422));

        RegistrationRows initialRows = registrationRows(tenantId);
        for (RejectedRegistration registration : registrations) {
            assertError(
                    post(tenantId, registration.path(), key("rejected"), registration.body()),
                    registration.status(),
                    registration.status() == 400 ? "bad-request" : "unprocessable-entity");
            assertEquals(initialRows, registrationRows(tenantId));
        }
    }

    @Test
    void commitsAndReplaysNaturalAndLegalRegistrationsAsAtomicSafeOutcomes() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        RegistrationRows initialRows = registrationRows(tenantId);
        String naturalKey = key("natural-atomic");
        String naturalValue = identifierValue("NA");
        String naturalBody = naturalBody(
                "Atomic Natural",
                "TEST_NATURAL_ACTIVE",
                naturalValue,
                null,
                null);

        Map<String, Object> natural = assertSafeRegistration(
                post(tenantId, "/v1/natural-person", naturalKey, naturalBody),
                "NATURAL_PERSON",
                "TEST_NATURAL_ACTIVE",
                naturalValue);
        assertEquals(initialRows.plus(new RegistrationRows(1, 1, 0, 1, 1, 2)), registrationRows(tenantId));
        assertEquals(natural, assertSuccess(post(
                tenantId,
                "/v1/natural-person",
                naturalKey,
                naturalBody), 201));
        assertEquals(initialRows.plus(new RegistrationRows(1, 1, 0, 1, 1, 2)), registrationRows(tenantId));

        String legalKey = key("legal-atomic");
        String legalValue = identifierValue("LA");
        String legalBody = legalBody(
                "Atomic Legal Ltd",
                "TEST_LEGAL_ACTIVE",
                legalValue,
                null,
                null);
        Map<String, Object> legal = assertSafeRegistration(
                post(tenantId, "/v1/legal-entity", legalKey, legalBody),
                "LEGAL_ENTITY",
                "TEST_LEGAL_ACTIVE",
                legalValue);
        assertEquals(initialRows.plus(new RegistrationRows(2, 1, 1, 2, 2, 4)), registrationRows(tenantId));
        assertEquals(legal, assertSuccess(post(
                tenantId,
                "/v1/legal-entity",
                legalKey,
                legalBody), 201));

        RegistrationRows completeRows = registrationRows(tenantId);
        assertError(
                post(
                        tenantId,
                        "/v1/natural-person",
                        naturalKey,
                        naturalBody("Atomic Natural", "TEST_NATURAL_ACTIVE", identifierValue("NC"), null, null)),
                409,
                "conflict");
        assertError(
                post(
                        tenantId,
                        "/v1/legal-entity",
                        legalKey,
                        legalBody("Atomic Legal Ltd", "TEST_LEGAL_ACTIVE", identifierValue("LC"), null, null)),
                409,
                "conflict");
        assertEquals(completeRows, registrationRows(tenantId));
    }

    @Test
    void rollsBackDuplicateAndDependencyFailuresWithoutPartialRows() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        String naturalValue = "  DUPLICATEN1234  ";
        assertSuccess(post(
                tenantId,
                "/v1/natural-person",
                key("natural-owner"),
                naturalBody("Natural Owner", "TEST_NATURAL_ACTIVE", naturalValue, null, null)), 201);
        RegistrationRows afterNaturalOwner = registrationRows(tenantId);
        assertError(
                post(
                        tenantId,
                        "/v1/natural-person",
                        key("natural-duplicate"),
                        naturalBody("Natural Duplicate", "TEST_NATURAL_ACTIVE", "duplicaten1234", null, null)),
                409,
                "conflict");
        assertEquals(afterNaturalOwner, registrationRows(tenantId));

        String legalValue = "  DUPLICATEL1234  ";
        assertSuccess(post(
                tenantId,
                "/v1/legal-entity",
                key("legal-owner"),
                legalBody("Legal Owner Ltd", "TEST_LEGAL_ACTIVE", legalValue, null, null)), 201);
        RegistrationRows afterLegalOwner = registrationRows(tenantId);
        assertError(
                post(
                        tenantId,
                        "/v1/legal-entity",
                        key("legal-duplicate"),
                        legalBody("Legal Duplicate Ltd", "TEST_LEGAL_ACTIVE", "duplicatel1234", null, null)),
                409,
                "conflict");
        assertEquals(afterLegalOwner, registrationRows(tenantId));

        assertError(
                post(
                        tenantId,
                        "/v1/natural-person",
                        key("natural-dependency"),
                        naturalBody("Natural Dependency", "TEST_NATURAL_ACTIVE", identifierValue("NF"), "SE", null)),
                503,
                "dependency-unavailable");
        assertEquals(afterLegalOwner, registrationRows(tenantId));
        assertError(
                post(
                        tenantId,
                        "/v1/legal-entity",
                        key("legal-dependency"),
                        legalBody("Legal Dependency Ltd", "TEST_LEGAL_ACTIVE", identifierValue("LF"), "SE", null)),
                503,
                "dependency-unavailable");
        assertEquals(afterLegalOwner, registrationRows(tenantId));
    }

    @Test
    void keepsCompleteAndNormalizedIdentifiersOutOfBodiesLogsSnapshotsAndOutbox() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        String idempotencyKey = key("confidentiality");
        String completeValue = "  leakprobeabc9876  ";
        String normalizedValue = "LEAKPROBEABC9876";
        List<String> capturedLogs = new CopyOnWriteArrayList<>();
        Logger rootLogger = Logger.getLogger("");
        Handler handler = capturingHandler(capturedLogs);
        rootLogger.addHandler(handler);

        Map<String, Object> created;
        try {
            String body = naturalBody(
                    "Confidential Natural",
                    "TEST_NATURAL_ACTIVE",
                    completeValue,
                    null,
                    null);
            Response createdResponse = post(
                    tenantId,
                    "/v1/natural-person",
                    idempotencyKey,
                    body);
            assertConfidential(createdResponse.asString(), completeValue, normalizedValue);
            created = assertSafeRegistration(
                    createdResponse,
                    "NATURAL_PERSON",
                    "TEST_NATURAL_ACTIVE",
                    normalizedValue);

            Response replayResponse = post(
                    tenantId,
                    "/v1/natural-person",
                    idempotencyKey,
                    body);
            assertConfidential(replayResponse.asString(), completeValue, normalizedValue);
            assertEquals(created, assertSuccess(replayResponse, 201));

            Response conflictResponse = post(
                    tenantId,
                    "/v1/natural-person",
                    idempotencyKey,
                    naturalBody(
                            "Changed Confidential Natural",
                            "TEST_NATURAL_ACTIVE",
                            completeValue,
                            null,
                            null));
            assertConfidential(conflictResponse.asString(), completeValue, normalizedValue);
            assertError(conflictResponse, 409, "conflict");
        } finally {
            rootLogger.removeHandler(handler);
            handler.close();
        }

        UUID partyId = UUID.fromString(string(created, "partyId"));
        UUID identifierId = UUID.fromString(string(nested(created, "initialIdentifier"), "identifierId"));
        IdentifierStorage storage = loadIdentifierStorage(identifierId);
        assertAuthenticatedCiphertext(storage.encryptedValue());
        assertEquals(1, storage.encryptionKeyVersion());
        assertTrue(storage.normalizedValueHash().matches("[0-9a-f]{64}"));
        assertEquals("************9876", storage.maskedValue());
        assertEquals(1, storage.normalizationVersion());
        assertConfidential(storage.encryptedValue(), completeValue, normalizedValue);
        assertConfidential(storage.normalizedValueHash(), completeValue, normalizedValue);

        String snapshot = loadSnapshot(tenantId, idempotencyKey);
        assertTrue(snapshot.contains(storage.maskedValue()));
        assertConfidential(snapshot, completeValue, normalizedValue);
        List<String> payloads = loadOutboxPayloads(tenantId, partyId, identifierId);
        assertEquals(2, payloads.size());
        payloads.forEach(payload -> assertConfidential(payload, completeValue, normalizedValue));
        assertTrue(capturedLogs.stream().anyMatch(log -> log.contains("Request completed")));
        assertConfidential(String.join("\n", capturedLogs), completeValue, normalizedValue);
    }

    private Map<String, Object> assertSafeRegistration(
            Response response,
            String partyType,
            String schemeCode,
            String forbiddenValue) {
        assertConfidential(response.asString(), forbiddenValue, forbiddenValue.strip().toUpperCase(Locale.ROOT));
        Map<String, Object> data = assertSuccess(response, 201);
        assertEquals(partyType, data.get("type"));
        assertEquals("DRAFT", data.get("recordStatus"));
        assertEquals(0, number(data, "version"));
        Map<String, Object> identifier = nested(data, "initialIdentifier");
        assertEquals(SAFE_IDENTIFIER_FIELDS, identifier.keySet());
        assertEquals(data.get("partyId"), identifier.get("partyId"));
        assertEquals(schemeCode, identifier.get("schemeCode"));
        assertEquals("PENDING_VERIFICATION", identifier.get("status"));
        assertEquals(0, number(identifier, "version"));
        assertFalse(identifier.containsKey("value"));
        assertFalse(identifier.containsKey("encryptedValue"));
        assertFalse(identifier.containsKey("normalizedValue"));
        assertFalse(identifier.containsKey("normalizedValueHash"));
        assertFalse(identifier.containsKey("encryptionKeyVersion"));
        return data;
    }

    private RegistrationRows registrationRows(UUID tenantId) {
        return new RegistrationRows(
                count("select count(party) from PartyEntity party where party.tenantId = :tenantId", tenantId),
                count("""
                        select count(details)
                        from NaturalPersonDetailsEntity details
                        where details.partyId in (
                            select party.id from PartyEntity party where party.tenantId = :tenantId
                        )
                        """, tenantId),
                count("""
                        select count(details)
                        from LegalEntityDetailsEntity details
                        where details.partyId in (
                            select party.id from PartyEntity party where party.tenantId = :tenantId
                        )
                        """, tenantId),
                count("""
                        select count(record)
                        from ApiIdempotencyRecordEntity record
                        where record.id.tenantId = :tenantId
                        """, tenantId),
                count("""
                        select count(identifier)
                        from PartyIdentifierEntity identifier
                        where identifier.tenantId = :tenantId
                        """, tenantId),
                count("""
                        select count(event)
                        from PartyOutboxEventEntity event
                        where event.tenantId = :tenantId
                        """, tenantId));
    }

    private long count(String query, UUID tenantId) {
        return awaitReactive(() -> sessionFactory.withSession(session -> session
                .createQuery(query, Long.class)
                .setParameter("tenantId", tenantId)
                .getSingleResult()));
    }

    private IdentifierStorage loadIdentifierStorage(UUID identifierId) {
        Object[] row = awaitReactive(() -> sessionFactory.withSession(session -> session
                .createNativeQuery("""
                        select encrypted_value,
                               encryption_key_version,
                               normalized_value_hash,
                               masked_value,
                               normalization_version
                        from party_identifiers
                        where id = :identifierId
                        """, Object[].class)
                .setParameter("identifierId", identifierId)
                .getSingleResult()));
        return new IdentifierStorage(
                (String) row[0],
                ((Number) row[1]).intValue(),
                ((String) row[2]).strip(),
                (String) row[3],
                ((Number) row[4]).intValue());
    }

    private String loadSnapshot(UUID tenantId, String idempotencyKey) {
        return awaitReactive(() -> sessionFactory.withSession(session -> session
                .createNativeQuery("""
                        select cast(result_snapshot as text)
                        from api_idempotency_records
                        where tenant_id = :tenantId
                          and operation = :operation
                          and idempotency_key = :idempotencyKey
                        """, String.class)
                .setParameter("tenantId", tenantId)
                .setParameter("operation", RegisterNaturalPersonCommand.OPERATION)
                .setParameter("idempotencyKey", idempotencyKey)
                .getSingleResult()));
    }

    private List<String> loadOutboxPayloads(
            UUID tenantId,
            UUID partyId,
            UUID identifierId) {
        return awaitReactive(() -> sessionFactory.withSession(session -> session
                .createNativeQuery("""
                        select cast(payload as text)
                        from party_outbox_events
                        where tenant_id = :tenantId
                          and aggregate_id in (:partyId, :identifierId)
                        order by event_type
                        """, String.class)
                .setParameter("tenantId", tenantId)
                .setParameter("partyId", partyId)
                .setParameter("identifierId", identifierId)
                .getResultList()));
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

    private static Response post(UUID tenantId, String path, String idempotencyKey, String body) {
        return request(tenantId)
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .body(body)
                .post(path);
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

    private static void assertError(Response response, int expectedStatus, String expectedCode) {
        assertEquals(expectedStatus, response.statusCode());
        assertEquals(PROCESS_ID, response.header(RequestContextFilter.PROCESS_ID_HEADER));
        Map<String, Object> envelope = response.jsonPath().getMap("$");
        assertEquals(ERROR_ENVELOPE_FIELDS, envelope.keySet());
        assertEquals(expectedStatus, number(envelope, "status"));
        assertEquals(expectedCode, envelope.get("code"));
        assertFalse(envelope.containsKey("data"));
        assertNull(response.jsonPath().get("data"));
    }

    private static void assertConfidential(
            String output,
            String completeValue,
            String normalizedValue) {
        String lowercaseOutput = output.toLowerCase(Locale.ROOT);
        for (String forbidden : List.of(
                completeValue.strip().toLowerCase(Locale.ROOT),
                normalizedValue.strip().toLowerCase(Locale.ROOT))) {
            assertFalse(lowercaseOutput.contains(forbidden),
                    () -> "Sensitive identifier appeared in observable output");
        }
    }

    private static void assertAuthenticatedCiphertext(String encryptedValue) {
        String[] components = encryptedValue.split("\\.", -1);
        assertEquals(3, components.length);
        assertEquals("v1", components[0]);
        assertEquals(12, java.util.Base64.getUrlDecoder().decode(components[1]).length);
        assertTrue(java.util.Base64.getUrlDecoder().decode(components[2]).length > 16);
    }

    private static Handler capturingHandler(List<String> capturedLogs) {
        return new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record == null) {
                    return;
                }
                List<String> parts = new ArrayList<>();
                parts.add(record.getMessage());
                if (record.getParameters() != null) {
                    parts.add(Arrays.toString(record.getParameters()));
                }
                if (record.getThrown() != null) {
                    parts.add(record.getThrown().toString());
                }
                capturedLogs.add(String.join(" ", parts));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }

    private static String naturalBody(
            String displayName,
            String schemeCode,
            String value,
            String birthCountryCode,
            String expiresOn) {
        String country = birthCountryCode == null
                ? ""
                : ",\"birthCountryCode\":\"" + birthCountryCode + "\"";
        return "{\"displayName\":\"" + displayName
                + "\",\"givenNames\":\"Ada\",\"familyNames\":\"Lovelace\""
                + country + ",\"initialIdentifier\":"
                + identifierBody(schemeCode, value, expiresOn) + "}";
    }

    private static String legalBody(
            String legalName,
            String schemeCode,
            String value,
            String incorporationCountryCode,
            String expiresOn) {
        String country = incorporationCountryCode == null ? "EC" : incorporationCountryCode;
        return "{\"legalName\":\"" + legalName
                + "\",\"incorporationCountryCode\":\"" + country
                + "\",\"initialIdentifier\":"
                + identifierBody(schemeCode, value, expiresOn) + "}";
    }

    private static String identifierBody(String schemeCode, String value, String expiresOn) {
        String expiration = expiresOn == null ? "" : ",\"expiresOn\":\"" + expiresOn + "\"";
        return "{\"identifierSchemeCode\":\"" + schemeCode
                + "\",\"value\":\"" + value + "\",\"isPrimary\":true" + expiration + "}";
    }

    private static String identifierValue(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
     * Describes one rejected HTTP registration case.
     */
    private record RejectedRegistration(String path, String body, int status) {
    }

    /**
     * Counts every persistence row owned by an isolated tenant's registrations.
     */
    private record RegistrationRows(
            long parties,
            long naturalDetails,
            long legalDetails,
            long idempotencyRecords,
            long identifiers,
            long outboxEvents) {

        private RegistrationRows plus(RegistrationRows added) {
            return new RegistrationRows(
                    parties + added.parties,
                    naturalDetails + added.naturalDetails,
                    legalDetails + added.legalDetails,
                    idempotencyRecords + added.idempotencyRecords,
                    identifiers + added.identifiers,
                    outboxEvents + added.outboxEvents);
        }
    }

    /**
     * Projects the approved protected columns of one persisted identifier.
     */
    private record IdentifierStorage(
            String encryptedValue,
            int encryptionKeyVersion,
            String normalizedValueHash,
            String maskedValue,
            int normalizationVersion) {
    }
}
