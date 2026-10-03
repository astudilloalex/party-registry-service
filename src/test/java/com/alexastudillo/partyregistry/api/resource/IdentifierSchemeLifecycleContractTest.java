package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.alexastudillo.partyregistry.api.resource.IdentifierSchemeHttpAssertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises all twelve lifecycle pairs, historical replay, withdrawal, no-body syntax, and failure precedence. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class IdentifierSchemeLifecycleContractTest {
    private static final List<String> ACTIONS = List.of("activate", "deprecate", "retire");

    @ParameterizedTest
    @CsvSource({"DRAFT,activate,ACTIVE", "DRAFT,deprecate,rejected", "DRAFT,retire,RETIRED",
            "ACTIVE,activate,rejected", "ACTIVE,deprecate,DEPRECATED", "ACTIVE,retire,RETIRED",
            "DEPRECATED,activate,rejected", "DEPRECATED,deprecate,rejected", "DEPRECATED,retire,RETIRED",
            "RETIRED,activate,rejected", "RETIRED,deprecate,rejected", "RETIRED,retire,rejected"})
    void obeysEveryPairAndPreservesConfigurationAndAuditOnRejectedActions(String state, String action, String target) {
        var context = Context.fresh();
        var before = inState(context, state);
        Response response = action(context, before, action, before.get("version").toString());
        if (target.equals("rejected")) {
            error(response, 409, "invalid-identifier-scheme-lifecycle", context);
            assertEquals(before, current(context, before));
        } else {
            var after = success(response, 200, context);
            var retained = new HashMap<>(after);
            retained.put("status", before.get("status"));
            retained.put("version", before.get("version"));
            retained.put("updatedAt", before.get("updatedAt"));
            assertEquals(before, retained);
            assertEquals(target, after.get("status"));
            assertEquals(((Number) before.get("version")).longValue() + 1, ((Number) after.get("version")).longValue());
            assertTrue(Instant.parse((String) after.get("updatedAt")).isAfter(Instant.parse((String) before.get("updatedAt"))));
            assertEquals(after, current(context, before));
            error(action(context, before, action, before.get("version").toString()), 412, "expected-version-mismatch", context);
            error(action(context, before, action, after.get("version").toString()), 409, "invalid-identifier-scheme-lifecycle", context);
        }
    }

    @Test
    void actionScopedReplayKeepsEveryHistoricalResultWithNewContextAndConflictsBeforeCurrentChecks() {
        var original = Context.fresh();
        var draft = create(original, uniqueCode());
        String key = "same-exact-action-key";
        var accepted = new HashMap<String, Map<String, Object>>();
        for (int index = 0; index < ACTIONS.size(); index++) {
            String action = ACTIONS.get(index);
            accepted.put(action, success(original.request().header("If-Match", Integer.toString(index)).header("Idempotency-Key", key)
                    .post(path(draft, action)), 200, original));
        }
        var retry = new Context(original.tenant(), "fresh-lifecycle-user", UUID.randomUUID().toString());
        var retired = current(retry, draft);
        for (int index = 0; index < ACTIONS.size(); index++) {
            String action = ACTIONS.get(index);
            String expected = Integer.toString(index);
            assertEquals(accepted.get(action), success(retry.request().header("If-Match", expected).header("Idempotency-Key", key)
                    .post(path(draft, action)), 200, retry));
            error(retry.request().header("If-Match", "3").header("Idempotency-Key", key).post(path(draft, action)), 409, "idempotency-key-conflict", retry);
            error(retry.request().header("If-Match", expected).header("Idempotency-Key", key).post(ROOT + "/" + UUID.randomUUID() + "/" + action),
                    409, "idempotency-key-conflict", retry);
            error(retry.request().header("If-Match", "invalid").header("Idempotency-Key", key).post(path(draft, action)), 400, "if-match-invalid", retry);
            var other = Context.fresh();
            error(other.request().header("If-Match", expected).header("Idempotency-Key", key).post(path(draft, action)), 412, "expected-version-mismatch", other);
        }
        assertEquals(retired, current(retry, draft));
        var other = Context.fresh();
        error(other.request().contentType("application/json").header("Idempotency-Key", key)
                .body(body((String) draft.get("code"))).post(ROOT), 409, "identifier-scheme-code-conflict", other);
        var deprecated = inState(original, "DEPRECATED");
        error(other.request().contentType("application/json").header("Idempotency-Key", "independent")
                .body(body((String) deprecated.get("code")).replace("TRIM_UPPERCASE_V1", "OBSOLETE")).post(ROOT), 409, "identifier-scheme-code-conflict", other);
    }

    @Test
    void failedLegacyActivationKeyIsReusableAfterRepairAndHistoricalWithdrawalNeedsNoSupportedKeys() {
        var context = Context.fresh();
        var draft = fixture(context, "HTTP_OBSOLETE_LIFECYCLE_DRAFT");
        String key = "repairable-activation";
        error(context.request().header("If-Match", "0").header("Idempotency-Key", key).post(path(draft, "activate")), 422, "invalid-identifier-scheme-configuration", context);
        assertEquals(draft, current(context, draft));
        error(context.request().contentType("application/json").header("If-Match", "0").body("{\"name\":\"Descriptive only\"}")
                .patch(ROOT + "/" + draft.get("id")), 422, "invalid-identifier-scheme-configuration", context);
        assertEquals(draft, current(context, draft));
        success(context.request().contentType("application/json").header("If-Match", "0").body("{\"normalizerKey\":\"TRIM_UPPERCASE_V1\"}")
                .patch(ROOT + "/" + draft.get("id")), 200, context);
        var active = success(context.request().header("If-Match", "1").header("Idempotency-Key", key).post(path(draft, "activate")), 200, context);
        success(action(context, active, "retire", "2"), 200, context);
        assertEquals(active, success(context.request().header("If-Match", "1").header("Idempotency-Key", key).post(path(draft, "activate")), 200, context));
        var historicalActive = fixture(context, "HTTP_OBSOLETE_LIFECYCLE_ACTIVE");
        var deprecated = success(action(context, historicalActive, "deprecate", "0"), 200, context);
        assertEquals("OBSOLETE_V1", deprecated.get("normalizerKey"));
        var historicalDeprecated = fixture(context, "HTTP_OBSOLETE_LIFECYCLE_DEPRECATED");
        error(action(context, historicalDeprecated, "activate", "0"), 409, "invalid-identifier-scheme-lifecycle", context);
        var retired = success(action(context, historicalDeprecated, "retire", "0"), 200, context);
        assertEquals("OBSOLETE_V1", retired.get("normalizerKey"));
    }

    @Test
    void statePrecedesExhaustionAndStaleVersionPrecedesBoth() {
        var context = Context.fresh();
        for (String state : List.of("DRAFT", "ACTIVE", "DEPRECATED")) {
            var before = fixture(context, "HTTP_EXHAUSTED_" + state);
            for (String action : ACTIONS) {
                boolean allowed = action.equals("retire") || action.equals("activate") && state.equals("DRAFT") || action.equals("deprecate") && state.equals("ACTIVE");
                error(action(context, before, action, Long.toString(Long.MAX_VALUE)), 409,
                        allowed ? "identifier-scheme-version-exhausted" : "invalid-identifier-scheme-lifecycle", context);
                error(action(context, before, action, "0"), 412, "expected-version-mismatch", context);
                assertEquals(before, current(context, before));
            }
        }
    }

    @Test
    void rejectsAnyBufferedBodyBeforeKeysAndKeysBeforeCanonicalIdAndVersion() {
        var context = Context.fresh();
        for (String action : ACTIONS) {
            verifyLifecyclePreconditions(context, action);
        }
    }

    private static void verifyLifecyclePreconditions(Context context, String action) {
        verifyBufferedBodyAndKeyRejections(context, ROOT + "/invalid/" + action);
        verifyIfMatchHeaderAndNotFound(context, ROOT + "/" + UUID.randomUUID() + "/" + action);
    }

    private static void verifyBufferedBodyAndKeyRejections(Context context, String invalid) {
        for (byte[] bytes : List.of(new byte[] {0}, new byte[] {(byte) 0xff}, "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                " ".getBytes(java.nio.charset.StandardCharsets.UTF_8), "null".getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            error(context.request().contentType("application/json").body(bytes).header("Idempotency-Key", " ").post(invalid), 400, "bad-request", context);
        }
        error(context.request().header("Idempotency-Key", " ").post(invalid), 400, "idempotency-key-blank", context);
        error(context.request().header("Idempotency-Key", "a", "b").post(invalid), 400, "idempotency-key-duplicated", context);
        error(context.request().header("Idempotency-Key", "x".repeat(129)).post(invalid), 400, "idempotency-key-too-long", context);
        error(context.request().post(invalid), 400, "identifier-scheme-id-invalid", context);
    }

    private static void verifyIfMatchHeaderAndNotFound(Context context, String absent) {
        error(context.request().post(absent), 400, "if-match-required", context);
        error(context.request().header("If-Match", "0", "0").post(absent), 400, "if-match-duplicated", context);
        for (String version : List.of("\"0\"", "00", "+1", "-1", "*")) {
            error(context.request().header("If-Match", version).post(absent), 400, "if-match-invalid", context);
        }
        error(context.request().header("If-Match", "9223372036854775808").post(absent), 400, "if-match-out-of-range", context);
        error(context.request().header("If-Match", "99").header("Idempotency-Key", "x".repeat(128)).body(new byte[0]).post(absent), 404, "identifier-scheme-not-found", context);
    }

    private static Map<String, Object> inState(Context context, String state) {
        var data = create(context, uniqueCode());
        if (state.equals("ACTIVE") || state.equals("DEPRECATED")) {
            data = success(action(context, data, "activate", "0"), 200, context);
        }
        if (state.equals("DEPRECATED")) data = success(action(context, data, "deprecate", "1"), 200, context);
        if (state.equals("RETIRED")) data = success(action(context, data, "retire", "0"), 200, context);
        return data;
    }

    private static Map<String, Object> fixture(Context context, String code) {
        return success(context.request().get(ROOT + "/by-code/" + code), 200, context);
    }

    private static Map<String, Object> current(Context context, Map<String, Object> data) {
        return success(context.request().get(ROOT + "/" + data.get("id")), 200, context);
    }

    private static String path(Map<String, Object> data, String action) {
        return ROOT + "/" + data.get("id") + "/" + action;
    }

    private static Response action(Context context, Map<String, Object> data, String action, String version) {
        return context.request().header("If-Match", version).post(path(data, action));
    }
}
