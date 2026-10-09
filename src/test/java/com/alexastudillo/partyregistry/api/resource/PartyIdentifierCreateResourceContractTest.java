package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.PartyIdentifierContractAssertions;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.FixedPartyIdentifierRegistrationProducer;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises strict real identifier POST binding, safe 201 results, exact replay identity and unchanged rejected outcomes. */
@QuarkusTest
@TestProfile(PartyIdentifierCreateResourceContractTest.RegistrationProfile.class)
@Timeout(120)
class PartyIdentifierCreateResourceContractTest {
    private static final String BASE = "/v1/parties/{party}/identifiers";
    private static final String USER = "original-identifier-operator";
    private static final String PROCESS = UUID.randomUUID().toString();
    private static final LocalDate DATE = FixedPartyIdentifierRegistrationProducer.DATE;

    @Inject AgroalDataSource database;
    @Inject ObjectMapper json;

    @Test
    void everyOwnedPartyTypeAndStateReceivesIndependentSafeVersionZeroEvidence() throws Exception {
        for (PartyType type : PartyType.values()) {
            for (PartyRecordStatus state : PartyRecordStatus.values()) {
                var owner = seed(type, state);
                String before = partySnapshot(owner);
                String value = value();
                JsonNode result = success(post(owner, key(), body(code(type), value)));
                assertEquals(owner.party().toString(), result.path("partyId").textValue());
                assertEquals("PENDING_VERIFICATION", result.path("status").textValue());
                assertEquals(0, result.path("version").longValue()); assertFalse(result.path("isPrimary").booleanValue());
                assertEquals(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().truncatedTo(ChronoUnit.MICROS).toString(), result.path("createdAt").textValue());
                assertEquals(result.path("createdAt"), result.path("updatedAt")); assertFalse(result.toString().contains(value));
                assertEquals(before, partySnapshot(owner)); assertEquals(List.of(1L, 1L), counts(owner));
            }
        }
    }

    @Test
    void strictShapeTypesDuplicatesAndTrailingTokensFailBeforeKeysAndPaths() throws Exception {
        var owner = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        String valid = body(code(owner.type()), value());
        for (String invalid : List.of("{", "[]", "42", valid + " {}", "null {}",
                "{\"identifierSchemeCode\":42,\"value\":\"VALID1234\"}", "{\"identifierSchemeCode\":\"TEST_NATURAL_ACTIVE\",\"value\":true}",
                with(valid, "\"status\":\"VERIFIED\""), with(valid, "\"version\":0"), with(valid, "\"extra\":null"),
                with(valid, "\"value\":\"different\""), with(valid, "\"va\\u006cue\":null"),
                with(valid, "\"issuerCode\":null,\"issuerCode\":null"), with(valid, "\"isPrimary\":\"false\""),
                with(valid, "\"isPrimary\":null"), with(valid, "\"isPrimary\":1"), with(valid, "\"issuedOn\":42"),
                with(valid, "\"expiresOn\":\"2026-02-30\""))) {
            error(request(owner).body(invalid).post(BASE, "invalid-party"), 400, "bad-request");
        }
        for (String required : List.of("", "null")) {
            error(request(owner).body(required).post(BASE, "invalid-party"), 400, "request-body-required");
        }
        assertEquals(List.of(0L, 0L), counts(owner));
    }

    @Test
    void rawLimitsAndRequiredRulesUseTheirDeclaredPrecedenceWithoutNormalization() throws Exception {
        var owner = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        var bodies = Map.of("identifier-scheme-code-required", "{}",
                "identifier-value-required", body(code(owner.type()), "   "),
                "identifier-scheme-code-too-long", body("A".repeat(65), "VALID1234"),
                "identifier-value-too-long", body(code(owner.type()), "A".repeat(257)),
                "issuer-code-too-long", with(body(code(owner.type()), value()), "\"issuerCode\":\"" + "😀".repeat(65) + "\""),
                "issuer-code-invalid", with(body(code(owner.type()), value()), "\"issuerCode\":\"invalid__authority\""));
        for (var expected : bodies.entrySet()) {
            error(request(owner).body(expected.getValue()).post(BASE, "invalid-party"), 400, expected.getKey());
        }
        String bothRequiredAndOversized = "{\"identifierSchemeCode\":null,\"value\":\"\",\"issuerCode\":\"" + "A".repeat(65) + "\"}";
        error(request(owner).body(bothRequiredAndOversized).post(BASE, "invalid-party"), 400, "identifier-scheme-code-required");
        error(request(owner).body(body(code(owner.type()), "😀".repeat(129))).post(BASE, "invalid-party"), 400, "identifier-value-too-long");
        assertEquals(List.of(0L, 0L), counts(owner));
    }

    @Test
    void creationKeyValidationPrecedesPartyPathAndPreservesCardinalityAndBounds() throws Exception {
        var owner = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        String valid = body(code(owner.type()), value());
        error(request(owner).body(valid).post(BASE, "invalid-party"), 400, "idempotency-key-required");
        error(request(owner).header("Idempotency-Key", "one", "two").body(valid).post(BASE, "invalid-party"), 400, "idempotency-key-duplicated");
        error(request(owner).header("Idempotency-Key", " ").body(valid).post(BASE, "invalid-party"), 400, "idempotency-key-blank");
        error(request(owner).header("Idempotency-Key", "k".repeat(129)).body(valid).post(BASE, "invalid-party"), 400, "idempotency-key-too-long");
        error(request(owner).header("Idempotency-Key", key()).body(valid).post(BASE, "invalid-party"), 400, "party-id-invalid");
        success(post(owner, "k".repeat(128), valid));
        assertEquals(List.of(1L, 1L), counts(owner));
    }

    @Test
    void bodyValidationStillPrecedesReplayAndInvalidContextHasNoOperationEffects() throws Exception {
        var owner = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        String key = key();
        String valid = body(code(owner.type()), value());
        success(post(owner, key, valid));
        error(request(owner).header("Idempotency-Key", key).body(with(valid, "\"isPrimary\":null")).post(BASE, owner.party()), 400, "bad-request");
        var invalidProcess = request(owner, USER, "invalid-process").body("{").post(BASE, owner.party());
        assertEquals(400, invalidProcess.statusCode()); assertNull(invalidProcess.header("Process-Id"));
        PartyIdentifierContractAssertions.assertErrorResponse(400, "process-id-invalid", json.readTree(invalidProcess.asString()));
        assertEquals(List.of(1L, 1L), counts(owner));
    }

    @Test
    void nullableDefaultsAndPropertyOrderingHaveOneHistoricalRetryIdentity() throws Exception {
        var owner = seed(PartyType.LEGAL_ENTITY, PartyRecordStatus.INACTIVE);
        String value = value();
        String key = key();
        JsonNode original = success(post(owner, key, body(code(owner.type()), value)));
        String equivalent = "{\"isPrimary\":false,\"expiresOn\":null,\"issuedOn\":null,\"issuerCode\":null,\"value\":\"" + value
                + "\",\"identifierSchemeCode\":\"" + code(owner.type()) + "\"}";
        assertEquals(original, success(post(owner, key, equivalent)));
        assertEquals(List.of(1L, 1L), counts(owner));
    }

    @Test
    void issuerCanonicalizationAndExactRawValuesDoNotRewriteFingerprintIdentity() throws Exception {
        var owner = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        String key = key();
        String raw = value().toLowerCase(Locale.ROOT);
        String valid = with(body(code(owner.type()), "  " + raw + "  "), "\"issuerCode\":\"civil_registry\",\"isPrimary\":true");
        JsonNode original = success(post(owner, key, valid));
        assertEquals("CIVIL_REGISTRY", original.path("issuerCode").textValue()); assertTrue(original.path("isPrimary").booleanValue());
        error(post(owner, key, valid.replace("civil_registry", "CIVIL_REGISTRY")), 409, "idempotency-key-conflict");
        error(post(owner, key, valid.replace("  " + raw + "  ", raw.toUpperCase(Locale.ROOT))), 409, "idempotency-key-conflict");
        assertEquals(original, success(post(owner, key, valid))); assertEquals(List.of(1L, 1L), counts(owner));
    }

    @Test
    void expirationIsOptionalAndInclusiveUsingOneControlledUtcDate() throws Exception {
        var owner = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        String code = IdentifierSchemeTestFixtures.BOTH_EXPIRING_CODE;
        for (String fields : List.of("", "\"expiresOn\":null", "\"expiresOn\":\"" + DATE + "\"")) {
            String valid = body(code, value());
            JsonNode result = success(post(owner, key(), fields.isEmpty() ? valid : with(valid, fields)));
            assertEquals("PENDING_VERIFICATION", result.path("status").textValue());
        }
        String past = with(body(code, value()), "\"expiresOn\":\"" + DATE.minusDays(1) + "\"");
        error(post(owner, key(), past), 422, "identifier-validation-failure");
        String inverted = with(body(code, value()), "\"issuedOn\":\"" + DATE.plusDays(2) + "\",\"expiresOn\":\"" + DATE.plusDays(1) + "\"");
        error(post(owner, key(), inverted), 422, "identifier-validation-failure");
        success(post(owner, key(), with(body(code, value()), "\"issuedOn\":\"" + DATE.plusDays(1) + "\"")));
        assertEquals(List.of(4L, 4L), counts(owner));
    }

    @Test
    void failedKeyIsReusableAndSchemeFailuresRetainTheirExactStableCodes() throws Exception {
        var owner = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED);
        String key = key();
        error(post(owner, key, body("UNKNOWN-SCHEME", value())), 422, "unknown-identifier-scheme");
        error(post(owner, key, body(IdentifierSchemeTestFixtures.BOTH_DRAFT_CODE, value())), 422, "inactive-identifier-scheme");
        error(post(owner, key, body(IdentifierSchemeTestFixtures.LEGAL_ACTIVE_CODE, value())), 422, "incompatible-identifier-scheme");
        assertEquals(List.of(0L, 0L), counts(owner));
        success(post(owner, key, body(code(owner.type()), value()))); assertEquals(List.of(1L, 1L), counts(owner));
    }

    @Test
    void normalizedDuplicatesConflictWithoutConsumingAnotherKeyOrMutatingHistory() throws Exception {
        var owner = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.ACTIVE);
        String value = value();
        JsonNode original = success(post(owner, key(), body(code(owner.type()), value)));
        String duplicateKey = key();
        error(post(owner, duplicateKey, body(code(owner.type()), value.toLowerCase(Locale.ROOT))), 409, "identifier-uniqueness-conflict");
        assertEquals(original, detail(owner, UUID.fromString(original.path("identifierId").textValue())));
        success(post(owner, duplicateKey, body(code(owner.type()), value()))); assertEquals(List.of(2L, 2L), counts(owner));
    }

    @Test
    void originalCreationReplaysAfterCurrentEvidenceAndPartyChangeWithFreshCorrelation() throws Exception {
        for (PartyType type : PartyType.values()) {
            var owner = seed(type, PartyRecordStatus.DRAFT);
            String key = key();
            String valid = with(body(code(type), value()), "\"issuerCode\":\"civil_registry\",\"isPrimary\":true");
            JsonNode original = success(post(owner, key, valid));
            UUID identifier = UUID.fromString(original.path("identifierId").textValue());
            mutateHistory(owner, identifier);
            String before = identifierSnapshot(identifier);
            String process = UUID.randomUUID().toString();
            var replay = request(owner, "retry-identifier-operator", process)
                    .header("Idempotency-Key", key).body(valid).post(BASE, owner.party());
            assertEquals(201, replay.statusCode()); assertEquals(process, replay.header("Process-Id"));
            JsonNode parsed = json.readTree(replay.asString());
            PartyIdentifierContractAssertions.assertItemResponse(201, parsed); assertEquals(original, parsed.path("data"));
            assertEquals(before, identifierSnapshot(identifier)); assertEquals(List.of(1L, 1L), counts(owner));
        }
    }

    @Test
    void provisionalCreationDoesNotDisplaceAnExistingVerifiedPrimary() throws Exception {
        var owner = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.ACTIVE);
        String firstKey = key();
        String firstBody = with(body(code(owner.type()), value()), "\"isPrimary\":true");
        JsonNode first = success(post(owner, firstKey, firstBody));
        UUID firstId = UUID.fromString(first.path("identifierId").textValue());
        var instant = Timestamp.from(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().plusSeconds(1));
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                UPDATE party_identifiers SET status = 'VERIFIED', verified_at = ?, verified_by = 'fixture-verifier',
                    version = 1, updated_at = ?, updated_by = 'fixture-verifier' WHERE id = ?
                """)) {
            statement.setTimestamp(1, instant); statement.setTimestamp(2, instant); statement.setObject(3, firstId);
            assertEquals(1, statement.executeUpdate());
        }
        String before = identifierSnapshot(firstId);
        JsonNode provisional = success(post(owner, key(), with(body(code(owner.type()), value()), "\"isPrimary\":true")));
        assertEquals("PENDING_VERIFICATION", provisional.path("status").textValue()); assertTrue(provisional.path("isPrimary").booleanValue());
        assertEquals(before, identifierSnapshot(firstId)); assertEquals(first, success(post(owner, firstKey, firstBody)));
        assertEquals(List.of(2L, 2L), counts(owner));
    }

    @Test
    void missingAndCrossTenantOwnersAreConcealedBeforeSchemeAdmission() throws Exception {
        var owner = seed(PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        String validSyntax = body("UNKNOWN-SCHEME", value());
        error(request(owner).header("Idempotency-Key", key()).body(validSyntax).post(BASE, UUID.randomUUID()), 404, "party-not-found");
        var crossTenant = new Owner(owner.party(), UUID.randomUUID(), owner.type());
        error(post(crossTenant, key(), validSyntax), 404, "party-not-found");
        assertEquals(List.of(0L, 0L), counts(owner)); assertEquals(List.of(0L, 0L), counts(crossTenant));
    }

    private JsonNode success(Response response) throws Exception {
        assertEquals(201, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        var body = json.readTree(response.asString()); PartyIdentifierContractAssertions.assertItemResponse(201, body);
        return body.path("data");
    }

    private void error(Response response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode()); assertEquals(PROCESS, response.header("Process-Id"));
        PartyIdentifierContractAssertions.assertErrorResponse(status, code, json.readTree(response.asString()));
    }

    private JsonNode detail(Owner owner, UUID identifier) throws Exception {
        var response = request(owner).get(BASE + "/{identifier}", owner.party(), identifier);
        assertEquals(200, response.statusCode()); return json.readTree(response.asString()).path("data");
    }

    private String body(String code, String value) throws Exception { return json.writeValueAsString(Map.of("identifierSchemeCode", code, "value", value)); }
    private static String with(String body, String fields) { return body.substring(0, body.length() - 1) + ',' + fields + '}'; }
    private static String value() { return "AB" + UUID.randomUUID().toString().replace("-", "").substring(0, 10); }
    private static String key() { return "create-" + UUID.randomUUID(); }
    private static String code(PartyType type) { return type == PartyType.NATURAL_PERSON ? IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE : IdentifierSchemeTestFixtures.LEGAL_ACTIVE_CODE; }
    private static Response post(Owner owner, String key, String body) { return request(owner).header("Idempotency-Key", key).body(body).post(BASE, owner.party()); }
    private static RequestSpecification request(Owner owner) {
        return request(owner, USER, PROCESS);
    }
    private static RequestSpecification request(Owner owner, String user, String process) {
        return given().contentType("application/json").header("Tenant-Id", owner.tenant().toString()).header("User-Id", user).header("Process-Id", process);
    }

    private Owner seed(PartyType type, PartyRecordStatus state) throws SQLException {
        var owner = new Owner(UUID.randomUUID(), UUID.randomUUID(), type);
        var instant = Timestamp.from(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().minusSeconds(60));
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                INSERT INTO parties(id, tenant_id, type, display_name, record_status, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, cast(? AS party_type), 'Identifier owner', cast(? AS party_record_status), 7, ?, 'fixture-creator', ?, 'fixture-creator')
                """)) {
            statement.setObject(1, owner.party()); statement.setObject(2, owner.tenant()); statement.setString(3, type.name()); statement.setString(4, state.name());
            statement.setTimestamp(5, instant); statement.setTimestamp(6, instant); assertEquals(1, statement.executeUpdate());
        }
        seedDetails(owner, instant);
        return owner;
    }

    private void seedDetails(Owner owner, Timestamp instant) throws SQLException {
        String sql = owner.type() == PartyType.NATURAL_PERSON ? """
                INSERT INTO natural_person_details(party_id, given_names, family_names, birth_date, created_at, created_by, updated_at, updated_by)
                VALUES (?, 'Given', 'Family', ?, ?, 'fixture-creator', ?, 'fixture-creator')
                """ : """
                INSERT INTO legal_entity_details(party_id, legal_name, incorporation_country_code, incorporated_on, created_at, created_by, updated_at, updated_by)
                VALUES (?, 'Registry Company', 'EC', ?, ?, 'fixture-creator', ?, 'fixture-creator')
                """;
        try (var connection = database.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, owner.party()); statement.setObject(2, LocalDate.of(1990, Month.JANUARY, 1));
            statement.setTimestamp(3, instant); statement.setTimestamp(4, instant); assertEquals(1, statement.executeUpdate());
        }
    }

    private List<Long> counts(Owner owner) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                SELECT (SELECT count(*) FROM party_identifiers WHERE tenant_id = ?),
                       (SELECT count(*) FROM party_identifier_idempotency_records WHERE tenant_id = ?)
                """)) {
            statement.setObject(1, owner.tenant()); statement.setObject(2, owner.tenant());
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return List.of(rows.getLong(1), rows.getLong(2)); }
        }
    }

    private String partySnapshot(Owner owner) throws SQLException { return snapshot("SELECT cast(to_jsonb(party) AS text) FROM parties party WHERE id = ?", owner.party()); }
    private String identifierSnapshot(UUID identifier) throws SQLException { return snapshot("SELECT cast(to_jsonb(identifier) AS text) FROM party_identifiers identifier WHERE id = ?", identifier); }
    private String snapshot(String sql, UUID id) throws SQLException {
        try (var connection = database.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getString(1); }
        }
    }

    private void mutateHistory(Owner owner, UUID identifier) throws SQLException {
        var changed = Timestamp.from(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().plusSeconds(1));
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("""
                UPDATE party_identifiers SET status = 'REVOKED', is_primary = false, version = 5, issuer_code = 'CHANGED_AUTHORITY',
                    updated_at = ?, updated_by = 'fixture-editor' WHERE tenant_id = ? AND party_id = ? AND id = ?
                """)) {
            statement.setTimestamp(1, changed); statement.setObject(2, owner.tenant()); statement.setObject(3, owner.party()); statement.setObject(4, identifier);
            assertEquals(1, statement.executeUpdate());
        }
        try (var connection = database.getConnection(); var statement = connection.prepareStatement("UPDATE parties SET record_status = 'ARCHIVED', version = 10 WHERE id = ?")) {
            statement.setObject(1, owner.party()); assertEquals(1, statement.executeUpdate());
        }
    }

    /** Identifies independently seeded owned records without adding subtype or initial-registration fixture dependencies. */
    private record Owner(UUID party, UUID tenant, PartyType type) { }

    /** Selects the fixed-clock real registration workflow and database-only container prerequisites. */
    public static class RegistrationProfile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() { return Map.of("quarkus.rabbitmq.devservices.enabled", "false"); }
        @Override public Set<Class<?>> getEnabledAlternatives() { return Set.of(FixedPartyIdentifierRegistrationProducer.class); }
    }
}
