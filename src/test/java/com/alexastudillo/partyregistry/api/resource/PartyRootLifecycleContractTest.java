package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.FixedLifecycleClockProducer;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.alexastudillo.partyregistry.support.RootPartyFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Verifies lifecycle HTTP transition, request-precedence and activation-evidence matrices against real reactive storage. */
@QuarkusTest
@TestProfile(PartyRootLifecycleContractTest.FixedClockProfile.class)
class PartyRootLifecycleContractTest {

    private static final String ROOT = "/v1/parties/";
    private static final String PROCESS = UUID.randomUUID().toString();
    private static final List<String> ACTIONS = List.of("activate", "deactivate", "archive");
    private static final LocalDate TODAY = LocalDate.ofInstant(FixedLifecycleClockProducer.CLOCK.instant(), ZoneOffset.UTC);

    @Inject
    RootPartyFixtures fixtures;
    @Inject
    Mutiny.SessionFactory sessions;

    @Test
    void bothTypesObeyEveryTransitionAndPreserveDetailsAndCreationAudit() {
        UUID tenant = UUID.randomUUID();
        for (PartyType type : PartyType.values()) {
            for (PartyRecordStatus status : PartyRecordStatus.values()) {
                for (String action : ACTIONS) {
                    UUID id = seed(tenant, type, status);
                    if (action.equals("activate")) {
                        evidence(tenant, id, compatible(type), "VERIFIED", TODAY, false);
                    }
                    Map<String, Object> before = current(tenant, id);
                    Response response = action(tenant, id, action, "0");
                    if (allowed(action, status)) {
                        Map<String, Object> after = success(response, resultingState(action), 1);
                        for (String field : List.of("partyId", "type", "displayName", "createdAt", "createdBy", "naturalPersonDetails", "legalEntityDetails")) {
                            assertEquals(before.get(field), after.get(field));
                        }
                        assertEquals(after, current(tenant, id));
                    } else {
                        error(response, 409, "invalid-party-lifecycle");
                        assertEquals(before, current(tenant, id));
                    }
                }
            }
        }
    }

    @Test
    void verifiedEvidenceUsesOneUtcDateAndDoesNotRequirePrimaryOrActiveScheme() {
        UUID tenant = UUID.randomUUID();
        for (PartyType type : PartyType.values()) {
            for (UUID scheme : List.of(compatible(type), IdentifierSchemeTestFixtures.BOTH_DEPRECATED_ID,
                    IdentifierSchemeTestFixtures.BOTH_RETIRED_ID, IdentifierSchemeTestFixtures.BOTH_DRAFT_ID)) {
                UUID id = seed(tenant, type, PartyRecordStatus.DRAFT);
                evidence(tenant, id, scheme, "VERIFIED", TODAY, false);
                success(action(tenant, id, "activate", "0"), "ACTIVE", 1);
            }
            UUID noExpiry = seed(tenant, type, PartyRecordStatus.DRAFT);
            evidence(tenant, noExpiry, IdentifierSchemeTestFixtures.BOTH_EXPIRING_ID, "VERIFIED", null, true);
            success(action(tenant, noExpiry, "activate", "0"), "ACTIVE", 1);
        }
    }

    @Test
    void ineligibleEvidenceLeavesTheDraftAndFailedKeyReusable() {
        UUID tenant = UUID.randomUUID();
        for (PartyType type : PartyType.values()) {
            for (String status : List.of("PENDING_VERIFICATION", "REJECTED", "REVOKED", "EXPIRED", "VERIFIED")) {
                UUID id = seed(tenant, type, PartyRecordStatus.DRAFT);
                LocalDate expiration = status.equals("VERIFIED") || status.equals("EXPIRED") ? TODAY.minusDays(1) : TODAY;
                evidence(tenant, id, compatible(type), status, expiration, true);
                Map<String, Object> before = current(tenant, id);
                String key = "failed-" + id;
                error(request(tenant).header("If-Match", "0").header("Idempotency-Key", key).post(ROOT + id + "/activate"),
                        422, "missing-qualifying-identifier");
                assertEquals(before, current(tenant, id));
                evidence(tenant, id, IdentifierSchemeTestFixtures.BOTH_RETIRED_ID, "VERIFIED", null, false);
                success(request(tenant).header("If-Match", "0").header("Idempotency-Key", key).post(ROOT + id + "/activate"), "ACTIVE", 1);
            }
            UUID incompatible = seed(tenant, type, PartyRecordStatus.DRAFT);
            UUID otherScheme = type == PartyType.NATURAL_PERSON ? IdentifierSchemeTestFixtures.LEGAL_ACTIVE_ID : IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID;
            evidence(tenant, incompatible, otherScheme, "VERIFIED", null, false);
            error(action(tenant, incompatible, "activate", "0"), 422, "missing-qualifying-identifier");
        }
    }

    @Test
    void unrelatedEvidenceAndCrossTenantTargetsAreConcealedBeforeBusinessChecks() {
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        UUID target = seed(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        UUID sibling = seed(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        UUID other = seed(otherTenant, PartyType.NATURAL_PERSON, PartyRecordStatus.DRAFT);
        evidence(tenant, sibling, compatible(PartyType.NATURAL_PERSON), "VERIFIED", null, true);
        evidence(otherTenant, other, compatible(PartyType.NATURAL_PERSON), "VERIFIED", null, true);
        error(action(tenant, target, "activate", "0"), 422, "missing-qualifying-identifier");
        for (String action : ACTIONS) {
            error(action(tenant, other, action, "99"), 404, "party-not-found");
            error(action(tenant, UUID.randomUUID(), action, "99"), 404, "party-not-found");
            UUID archived = seed(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.ARCHIVED);
            error(action(tenant, archived, action, "1"), 412, "stale-party-version");
            error(action(tenant, archived, action, "0"), 409, "invalid-party-lifecycle");
        }
    }

    @Test
    void allActionsValidateOptionalKeysPathAndRequiredVersionInOrder() {
        UUID tenant = UUID.randomUUID();
        for (String action : ACTIONS) {
            String invalidPath = ROOT + "invalid/" + action;
            error(request(tenant).header("Idempotency-Key", " ").post(invalidPath), 400, "idempotency-key-blank");
            error(request(tenant).header("Idempotency-Key", "first", "second").post(invalidPath), 400, "idempotency-key-duplicated");
            error(request(tenant).header("Idempotency-Key", "x".repeat(129)).post(invalidPath), 400, "idempotency-key-too-long");
            error(request(tenant).post(invalidPath), 400, "party-id-invalid");
            UUID id = seed(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED);
            String path = ROOT + id + "/" + action;
            error(request(tenant).post(path), 400, "if-match-required");
            error(request(tenant).header("If-Match", "0", "0").post(path), 400, "if-match-duplicated");
            for (String value : List.of("\"0\"", "-1", "00", "*", "+1")) {
                error(action(tenant, id, action, value), 400, "if-match-invalid");
            }
            error(action(tenant, id, action, "9223372036854775808"), 400, "if-match-out-of-range");
            error(request(tenant).header("If-Match", "0").header("Idempotency-Key", "x".repeat(128)).post(path), 409, "invalid-party-lifecycle");
            assertEquals(0, current(tenant, id).get("version"));
        }
    }

    @Test
    void completedKeysConflictBeforeCurrentStateButNeverSkipSyntaxValidation() {
        UUID tenant = UUID.randomUUID();
        UUID id = seed(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.DRAFT);
        String key = "archive-" + id;
        success(request(tenant).header("If-Match", "0").header("Idempotency-Key", key).post(ROOT + id + "/archive"), "ARCHIVED", 1);
        error(request(tenant).header("If-Match", "1").header("Idempotency-Key", key).post(ROOT + id + "/archive"), 409, "idempotency-key-conflict");
        error(request(tenant).header("If-Match", "0").header("Idempotency-Key", key).post(ROOT + UUID.randomUUID() + "/archive"), 409, "idempotency-key-conflict");
        error(request(tenant).header("If-Match", "invalid").header("Idempotency-Key", key).post(ROOT + id + "/archive"), 400, "if-match-invalid");
        error(request(tenant).header("If-Match", "0").header("Idempotency-Key", key).post(ROOT + "invalid/archive"), 400, "party-id-invalid");
        error(action(tenant, id, "archive", "0"), 412, "stale-party-version");
        success(request(tenant).header("If-Match", "0").header("Idempotency-Key", key).post(ROOT + id + "/archive"), "ARCHIVED", 1);
    }

    private UUID seed(UUID tenant, PartyType type, PartyRecordStatus state) {
        return RootPartyFixtures.await(() -> fixtures.create(tenant, type, state, "Historical", FixedLifecycleClockProducer.CLOCK.instant().minusSeconds(60)));
    }

    private void evidence(UUID tenant, UUID id, UUID scheme, String status, LocalDate expiration, boolean primary) {
        RootPartyFixtures.await(() -> sessions.withTransaction((session, transaction) -> session.createNativeQuery("""
                insert into party_identifiers (tenant_id, party_id, identifier_scheme_id, encrypted_value, encryption_key_version,
                    normalized_value_hash, masked_value, status, expires_on, is_primary, verified_at, verified_by, created_by, updated_by)
                values (:tenant, :party, :scheme, 'test-only-unread-ciphertext', 1, :hash, '***1234',
                    cast(:status as party_identifier_status), :expiration, :primary, :verifiedAt, 'fixture-verifier', 'fixture', 'fixture')
                """).setParameter("tenant", tenant).setParameter("party", id).setParameter("scheme", scheme)
                .setParameter("hash", UUID.randomUUID().toString().replace("-", "").repeat(2)).setParameter("status", status)
                .setParameter("expiration", expiration).setParameter("primary", primary)
                .setParameter("verifiedAt", FixedLifecycleClockProducer.CLOCK.instant().minusSeconds(1)).executeUpdate()));
    }

    private static UUID compatible(PartyType type) {
        return type == PartyType.NATURAL_PERSON ? IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID : IdentifierSchemeTestFixtures.LEGAL_ACTIVE_ID;
    }

    private static boolean allowed(String action, PartyRecordStatus state) {
        return switch (action) {
            case "activate" -> state == PartyRecordStatus.DRAFT;
            case "deactivate" -> state == PartyRecordStatus.ACTIVE;
            case "archive" -> state != PartyRecordStatus.ARCHIVED;
            default -> throw new IllegalArgumentException("Unknown test action");
        };
    }

    private static String resultingState(String action) {
        return switch (action) {
            case "activate" -> "ACTIVE";
            case "deactivate" -> "INACTIVE";
            case "archive" -> "ARCHIVED";
            default -> throw new IllegalArgumentException("Unknown test action");
        };
    }

    private static RequestSpecification request(UUID tenant) {
        return given().header("Tenant-Id", tenant.toString()).header("User-Id", "lifecycle-operator").header("Process-Id", PROCESS);
    }

    private static Response action(UUID tenant, UUID id, String action, String version) {
        return request(tenant).header("If-Match", version).post(ROOT + id + "/" + action);
    }

    private static Map<String, Object> current(UUID tenant, UUID id) {
        Response response = request(tenant).get(ROOT + id);
        assertEquals(200, response.statusCode());
        return response.jsonPath().getMap("data");
    }

    private static Map<String, Object> success(Response response, String state, int version) {
        assertEquals(200, response.statusCode(), response.asString());
        assertEquals(Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
        assertEquals(200, response.jsonPath().getInt("status"));
        assertEquals("successful", response.jsonPath().getString("code"));
        assertEquals(PROCESS, response.header("Process-Id"));
        assertEquals(state, response.jsonPath().getString("data.recordStatus"));
        assertEquals(version, response.jsonPath().getInt("data.version"));
        assertEquals("lifecycle-operator", response.jsonPath().getString("data.updatedBy"));
        assertEquals(FixedLifecycleClockProducer.CLOCK.instant().toString(), response.jsonPath().getString("data.updatedAt"));
        assertFalse(response.asString().contains("ciphertext"));
        return response.jsonPath().getMap("data");
    }

    private static void error(Response response, int status, String code) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(Map.of("status", status, "code", code), response.jsonPath().getMap("$"));
        assertEquals(PROCESS, response.header("Process-Id"));
    }

    /** Selects only the fixed lifecycle producer while retaining real storage, migrations, and stored-only events. */
    public static final class FixedClockProfile implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(FixedLifecycleClockProducer.class);
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("party-registry.outbox.mode", "stored-only", "quarkus.rabbitmq.devservices.enabled", "false");
        }
    }
}
