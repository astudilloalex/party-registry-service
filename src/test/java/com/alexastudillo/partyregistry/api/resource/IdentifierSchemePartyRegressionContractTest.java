package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.support.StoredOutboxTestProfile;
import com.alexastudillo.partyregistry.infrastructure.integration.geographic.GeographicReferenceStubResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.restassured.response.Response;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.alexastudillo.partyregistry.api.resource.IdentifierSchemeHttpAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies catalog administration affects future admission while preserving every historical Party/evidence column and emitting no Party event. */
@QuarkusTest
@TestProfile(StoredOutboxTestProfile.class)
@QuarkusTestResource(GeographicReferenceStubResource.class)
@Timeout(120)
class IdentifierSchemePartyRegressionContractTest {
    @Inject Mutiny.SessionFactory sessions;
    @Inject Vertx vertx;
    @TestHTTPResource URI server;

    protected int firstHistoricalSlot() { return 1; }

    @Test @RunOnVertxContext
    void newInitialAndAdditionalAdmissionFollowsLiveLifecycleWithOptionalExpiration(UniAsserter asserter) {
        for (String type : List.of("NATURAL_PERSON", "LEGAL_ENTITY")) {
            verifyLiveLifecycleWithOptionalExpiration(asserter, type);
        }
    }

    private void verifyLiveLifecycleWithOptionalExpiration(UniAsserter asserter, String type) {
        var context = Context.fresh();
        String code = uniqueCode();
        var scheme = new AtomicReference<Map<String, Object>>();
        var party = new AtomicReference<Map<String, Object>>();
        var baseline = new AtomicReference<String>();
        var catalogEvents = new AtomicReference<Long>();
        asserter.execute(() -> send(context, "POST", ROOT, body(code).replace("\"name\":", "\"requiresExpiration\":true,\"name\":"),
                Map.of("Idempotency-Key", UUID.randomUUID().toString())).invoke(response -> scheme.set(success(response, 201, context))));
        asserter.execute(() -> initial(context, type, "TEST_BOTH_EXPIRING").invoke(response -> party.set(partySuccess(response, 201, context))));
        asserter.execute(() -> snapshot((String) party.get().get("partyId")).invoke(baseline::set));
        asserter.execute(() -> eventCount(context).invoke(catalogEvents::set));
        asserter.assertThat(() -> initial(context, type, code), response -> error(response, 422, "inactive-identifier-scheme", context));
        asserter.assertThat(() -> additional(context, party.get(), code), response -> error(response, 422, "inactive-identifier-scheme", context));
        asserter.execute(() -> action(context, scheme.get(), "activate").invoke(response -> scheme.set(success(response, 200, context))));
        asserter.assertThat(() -> snapshot((String) party.get().get("partyId")), actual -> assertEquals(baseline.get(), actual));
        asserter.assertThat(() -> eventCount(context), actual -> assertEquals(catalogEvents.get(), actual));
        asserter.assertThat(() -> initial(context, type, code), response -> {
            var created = partySuccess(response, 201, context);
            assertNull(response.jsonPath().get("data.initialIdentifier.expiresOn"));
            assertEquals("DRAFT", created.get("recordStatus"));
        });
        asserter.assertThat(() -> additional(context, party.get(), code), response -> {
            partySuccess(response, 201, context);
            assertNull(response.jsonPath().get("data.expiresOn"));
        });
        asserter.execute(() -> snapshot((String) party.get().get("partyId")).invoke(baseline::set));
        verifyWithdrawalRejections(asserter, context, type, code, scheme, party, baseline, catalogEvents);
        asserter.assertThat(() -> tenantRows(context), actual -> assertEquals(List.of(2L, 3L, 2L), actual));
    }

    private void verifyWithdrawalRejections(UniAsserter asserter, Context context, String type, String code,
            AtomicReference<Map<String, Object>> scheme, AtomicReference<Map<String, Object>> party,
            AtomicReference<String> baseline, AtomicReference<Long> catalogEvents) {
        for (String action : List.of("deprecate", "retire")) {
            asserter.execute(() -> eventCount(context).invoke(catalogEvents::set));
            asserter.execute(() -> action(context, scheme.get(), action).invoke(response -> scheme.set(success(response, 200, context))));
            asserter.assertThat(() -> snapshot((String) party.get().get("partyId")), actual -> assertEquals(baseline.get(), actual));
            asserter.assertThat(() -> eventCount(context), actual -> assertEquals(catalogEvents.get(), actual));
            asserter.assertThat(() -> initial(context, type, code), response -> error(response, 422, "inactive-identifier-scheme", context));
            asserter.assertThat(() -> additional(context, party.get(), code), response -> error(response, 422, "inactive-identifier-scheme", context));
            asserter.assertThat(() -> snapshot((String) party.get().get("partyId")), actual -> assertEquals(baseline.get(), actual));
            asserter.assertThat(() -> eventCount(context), actual -> assertEquals(catalogEvents.get(), actual));
        }
    }

    @Test @RunOnVertxContext
    void qualifiedNonprimaryVerifiedHistoricalEvidenceStillActivatesBothPartyTypesAfterWithdrawal(UniAsserter asserter) {
        for (int slot = firstHistoricalSlot(); slot < firstHistoricalSlot() + 4; slot++) {
            verifyHistoricalEvidenceSlot(asserter, slot);
        }
    }

    private void verifyHistoricalEvidenceSlot(UniAsserter asserter, int slot) {
        String suffix = "%03d".formatted(slot);
        String partyId = "0198d114-08f1-7e48-b291-399bbb9cd" + suffix;
        String schemeId = "0198d113-08f1-7e48-b291-399bbb9cd" + suffix;
        var context = new Context("0198d116-08f1-7e48-b291-399bbb9cd" + suffix, "catalog-history-operator", UUID.randomUUID().toString());
        String withdrawal = (slot - firstHistoricalSlot()) < 2 ? "deprecate" : "retire";
        var before = new AtomicReference<String>();
        var scheme = new AtomicReference<Map<String, Object>>();
        verifyEvidenceAndSchemeBaseline(asserter, partyId, schemeId, context, before, scheme);
        verifyWithdrawalActivationAndState(asserter, partyId, context, withdrawal, before, scheme);
    }

    private void verifyEvidenceAndSchemeBaseline(UniAsserter asserter, String partyId, String schemeId,
            Context context, AtomicReference<String> before, AtomicReference<Map<String, Object>> scheme) {
        asserter.execute(() -> snapshot(partyId).invoke(value -> {
            before.set(value);
            var evidence = new JsonObject(value).getJsonArray("identifiers").getJsonObject(0);
            assertEquals(false, evidence.getBoolean("is_primary"));
            assertEquals("VERIFIED", evidence.getString("status"));
            assertNull(evidence.getValue("expires_on"));
            assertEquals(7, evidence.getInteger("version"));
            assertEquals(2, evidence.getInteger("encryption_key_version"));
            assertEquals(3, evidence.getInteger("normalization_version"));
        }));
        asserter.execute(() -> send(context, "GET", ROOT + "/" + schemeId, null, Map.of()).invoke(response -> {
            scheme.set(success(response, 200, context));
            assertEquals(true, scheme.get().get("requiresExpiration"));
            assertEquals("OBSOLETE_NORMALIZER_V1", scheme.get().get("normalizerKey"));
        }));
        asserter.assertThat(() -> eventCount(context), actual -> assertEquals(0L, actual));
    }

    private void verifyWithdrawalActivationAndState(UniAsserter asserter, String partyId, Context context,
            String withdrawal, AtomicReference<String> before, AtomicReference<Map<String, Object>> scheme) {
        asserter.execute(() -> action(context, scheme.get(), withdrawal).invoke(response -> scheme.set(success(response, 200, context))));
        asserter.assertThat(() -> snapshot(partyId), actual -> assertEquals(before.get(), actual));
        asserter.assertThat(() -> eventCount(context), actual -> assertEquals(0L, actual));
        asserter.assertThat(() -> send(context, "POST", "/v1/parties/" + partyId + "/activate", null, Map.of("If-Match", "0")), response -> {
            var accepted = partySuccess(response, 200, context);
            assertEquals("ACTIVE", accepted.get("recordStatus"));
            assertEquals(1, accepted.get("version"));
        });
        asserter.assertThat(() -> snapshot(partyId), value -> assertSnapshotAfterActivation(context, before.get(), value));
        asserter.assertThat(() -> eventCount(context), actual -> assertEquals(1L, actual));
    }

    private void assertSnapshotAfterActivation(Context context, String beforeJson, String currentJson) {
        var original = new JsonObject(beforeJson);
        var current = new JsonObject(currentJson);
        assertEquals(original.getValue("identifiers"), current.getValue("identifiers"));
        assertEquals(original.getValue("natural"), current.getValue("natural"));
        assertEquals(original.getValue("legal"), current.getValue("legal"));
        var oldParty = original.getJsonObject("party");
        var newParty = current.getJsonObject("party");
        assertEquals("ACTIVE", newParty.remove("record_status"));
        assertEquals(1, ((Number) newParty.remove("version")).intValue());
        assertEquals(context.user(), newParty.remove("updated_by"));
        assertNotEquals(oldParty.getValue("updated_at"), newParty.remove("updated_at"));
        for (String field : List.of("record_status", "version", "updated_by", "updated_at")) {
            oldParty.remove(field);
        }
        assertEquals(oldParty, newParty);
    }

    private Uni<Response> initial(Context context, String type, String code) {
        String input = type.equals("NATURAL_PERSON") ? "{\"givenNames\":\"Catalog\",\"familyNames\":\"Regression\",\"initialIdentifier\":%s}"
                : "{\"legalName\":\"Catalog Regression Ltd\",\"incorporationCountryCode\":\"EC\",\"initialIdentifier\":%s}";
        return Uni.createFrom().deferred(() -> {
            var allowed = GeographicReferenceStubResource.allowContext(context.tenant(), context.user(), context.process());
            return send(context, "POST", type.equals("NATURAL_PERSON") ? "/v1/natural-person" : "/v1/legal-entity", input.formatted(identifier(code)),
                    Map.of("Idempotency-Key", UUID.randomUUID().toString())).eventually(() -> {
                        try { allowed.close(); }
                        catch (Exception failure) { throw new AssertionError("Cannot clear geographic test context", failure); }
                    });
        });
    }
    private Uni<Response> additional(Context context, Map<String, Object> party, String code) {
        return send(context, "POST", "/v1/parties/" + party.get("partyId") + "/identifiers", identifier(code), Map.of("Idempotency-Key", UUID.randomUUID().toString()));
    }
    private static String identifier(String code) {
        return "{\"identifierSchemeCode\":\"" + code + "\",\"value\":\"ID" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "\",\"isPrimary\":false}";
    }
    private Uni<Response> action(Context context, Map<String, Object> scheme, String action) {
        return send(context, "POST", ROOT + "/" + scheme.get("id") + "/" + action, null, Map.of("If-Match", scheme.get("version").toString()));
    }
    private Uni<Response> send(Context context, String method, String path, String body, Map<String, String> headers) {
        return IdentifierSchemeReactiveHttp.send(vertx, server, context, method, path, body, headers);
    }
    private static Map<String, Object> partySuccess(Response response, int status, Context context) {
        assertEquals(status, response.statusCode(), response.asString()); assertEquals(context.process(), response.header("Process-Id"));
        assertEquals(Set.of("status", "code", "data"), response.jsonPath().getMap("$").keySet());
        assertEquals(status, response.jsonPath().getInt("status")); assertEquals("successful", response.jsonPath().getString("code"));
        return response.jsonPath().getMap("data");
    }
    private Uni<String> snapshot(String partyId) {
        return sessions.withSession(session -> session.createNativeQuery("""
                select jsonb_build_object('party', to_jsonb(p),
                  'natural', (select to_jsonb(n) from natural_person_details n where n.party_id = p.id),
                  'legal', (select to_jsonb(l) from legal_entity_details l where l.party_id = p.id),
                  'identifiers', (select jsonb_agg(to_jsonb(i) order by i.id) from party_identifiers i where i.party_id = p.id))::text
                from parties p where p.id = :id
                """, String.class).setParameter("id", UUID.fromString(partyId)).getSingleResult());
    }
    private Uni<Long> eventCount(Context context) {
        return sessions.withSession(session -> session.createNativeQuery("select count(*) from party_outbox_events where tenant_id = :tenant", Long.class)
                .setParameter("tenant", UUID.fromString(context.tenant())).getSingleResult());
    }
    private Uni<List<Long>> tenantRows(Context context) {
        return sessions.withSession(session -> session.createNativeQuery("""
                select (select count(*) from parties where tenant_id = :tenant),
                       (select count(*) from party_identifiers where tenant_id = :tenant),
                       (select count(*) from api_idempotency_records where tenant_id = :tenant)
                """, Object[].class).setParameter("tenant", UUID.fromString(context.tenant())).getSingleResult()
                .map(row -> List.of(((Number) row[0]).longValue(), ((Number) row[1]).longValue(), ((Number) row[2]).longValue())));
    }
}
