package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.application.command.ChangeIdentifierSchemeLifecycleCommand;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleAction;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.usecase.ChangeIdentifierSchemeLifecycleUseCase;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import com.alexastudillo.partyregistry.support.RootPartyFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.alexastudillo.partyregistry.api.resource.IdentifierSchemeHttpAssertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises every editable property/state, exact presence, legacy descriptive maintenance, and ordered business failures. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class IdentifierSchemePatchContractTest {
    @Inject ChangeIdentifierSchemeLifecycleUseCase lifecycle;

    @Test
    void coversEveryPropertyInEveryStateIncludingEqualLockedValuesAndUnchangedFailures() {
        var context = Context.fresh();
        var edits = Map.of("name", "\"Exact Mixed Name\"", "description", "\"Descriptive\"", "normalizerKey", "\"TRIM_UPPERCASE_V1\"",
                "validatorKey", "\"ALPHANUMERIC_V1\"", "minimumLength", "1", "maximumLength", "32767", "requiresExpiration", "false");
        for (String state : List.of("DRAFT", "ACTIVE", "DEPRECATED", "RETIRED")) {
            for (var edit : edits.entrySet()) {
                var before = inState(context, state);
                String patch = "{\"" + edit.getKey() + "\":" + edit.getValue() + "}";
                Response response = patch(context, before, patch, before.get("version").toString());
                if (state.equals("RETIRED")) {
                    error(response, 409, "identifier-scheme-retired", context);
                    assertEquals(before, current(context, before));
                } else if (!state.equals("DRAFT") && !List.of("name", "description").contains(edit.getKey())) {
                    error(response, 409, "identifier-scheme-rules-locked", context);
                    assertEquals(before, current(context, before));
                } else {
                    var after = success(response, 200, context);
                    assertEquals(((Number) before.get("version")).longValue() + 1, ((Number) after.get("version")).longValue());
                    assertEquals(state, after.get("status"));
                    assertEquals(before.get("createdAt"), after.get("createdAt"));
                    assertTrue(Instant.parse((String) after.get("updatedAt")).isAfter(Instant.parse((String) before.get("updatedAt"))));
                    assertEquals(after, current(context, before));
                }
            }
        }
    }

    @Test
    void preservesOmittedBoundsClearsNullableFieldsAndValidatesRetainedRangeWithoutPartialChanges() {
        var context = Context.fresh();
        var draft = create(context, uniqueCode());
        var configured = success(patch(context, draft, "{\"description\":\"Kept\",\"minimumLength\":10,\"maximumLength\":20,\"requiresExpiration\":true}", "0"), 200, context);
        var renamed = success(patch(context, draft, "{\"name\":\"New\",\"description\":null}", "1"), 200, context);
        assertEquals(10, renamed.get("minimumLength"));
        assertEquals(20, renamed.get("maximumLength"));
        assertEquals(true, renamed.get("requiresExpiration"));
        assertFalse(renamed.containsKey("description"));
        error(patch(context, draft, "{\"name\":\"Rejected\",\"maximumLength\":5}", "2"), 422, "identifier-scheme-length-range-invalid", context);
        assertEquals(renamed, current(context, draft));
        var cleared = success(patch(context, draft, "{\"minimumLength\":null,\"maximumLength\":null}", "2"), 200, context);
        assertFalse(cleared.containsKey("minimumLength"));
        assertFalse(cleared.containsKey("maximumLength"));
        assertEquals(configured.get("createdAt"), cleared.get("createdAt"));
    }

    @Test
    void legacyDescriptiveMaintenanceDoesNotReadmitObsoleteKeysAndExhaustionPrecedesSemanticBounds() {
        var context = Context.fresh();
        for (String state : List.of("ACTIVE", "DEPRECATED")) {
            var before = success(context.request().get(ROOT + "/by-code/HTTP_OBSOLETE_PATCH_" + state), 200, context);
            var after = success(patch(context, before, "{\"name\":\"Historical descriptive edit\",\"description\":null}", before.get("version").toString()), 200, context);
            assertEquals("OBSOLETE_V1", after.get("normalizerKey"));
            assertEquals(state, after.get("status"));
            error(patch(context, before, "{\"name\":\"Rejected\",\"minimumLength\":null}", after.get("version").toString()), 409, "identifier-scheme-rules-locked", context);
            assertEquals(after, current(context, before));
        }
        for (String state : List.of("DRAFT", "ACTIVE", "DEPRECATED")) {
            var before = success(context.request().get(ROOT + "/by-code/HTTP_EXHAUSTED_" + state), 200, context);
            assertEquals(Long.MAX_VALUE, ((Number) before.get("version")).longValue());
            String edit = state.equals("DRAFT") ? "{\"minimumLength\":" + "9".repeat(300) + "}" : "{\"name\":\"Valid\"}";
            error(patch(context, before, edit, Long.toString(Long.MAX_VALUE)), 409, "identifier-scheme-version-exhausted", context);
            assertEquals(before, current(context, before));
        }
    }

    @Test
    void strictBodyPathHeaderAndBusinessPrecedenceIsStableForHugeIntegralInputs() {
        var context = Context.fresh();
        for (var entry : Map.of("", "request-body-required", "null", "request-body-required", "{}", "patch-property-required").entrySet()) {
            error(context.request().contentType("application/json").body(entry.getKey()).patch(ROOT + "/invalid"), 400, entry.getValue(), context);
        }
        for (String invalid : List.of("[]", "{", "{\"name\":\"a\",\"name\":\"b\"}", "{\"requiresExpiration\":null}",
                "{\"name\":null}", "{\"minimumLength\":1.5}", "{\"maximumLength\":\"10\"}")) {
            error(context.request().contentType("application/json").body(invalid).patch(ROOT + "/invalid"), 400, "bad-request", context);
        }
        for (String immutable : List.of("id", "code", "issuingCountryCode", "category", "applicableSubjectType", "status", "version", "createdAt", "updatedAt", "createdBy", "unknown")) {
            error(context.request().contentType("application/json").body("{\"name\":\"Valid\",\"" + immutable + "\":null}").patch(ROOT + "/invalid"), 400, "bad-request", context);
        }
        error(context.request().contentType("application/json").body("{\"name\":\"Valid\"}").patch(ROOT + "/invalid"), 400, "identifier-scheme-id-invalid", context);
        var draft = create(context, uniqueCode());
        String path = ROOT + "/" + draft.get("id");
        error(context.request().contentType("application/json").body("{\"name\":\"Valid\"}").patch(path), 400, "if-match-required", context);
        error(context.request().contentType("application/json").body("{\"name\":\"Valid\"}").header("If-Match", "0", "0").patch(path), 400, "if-match-duplicated", context);
        for (String version : List.of("\"0\"", "00", "-1", "+1", "*")) {
            error(patch(context, draft, "{\"name\":\"Valid\"}", version), 400, "if-match-invalid", context);
        }
        error(patch(context, draft, "{\"name\":\"Valid\"}", "9223372036854775808"), 400, "if-match-out-of-range", context);
        String huge = "{\"minimumLength\":" + "9".repeat(300) + "}";
        error(context.request().contentType("application/json").body(huge).header("If-Match", "99").patch(ROOT + "/" + UUID.randomUUID()), 404, "identifier-scheme-not-found", context);
        error(patch(context, draft, huge, "99"), 412, "expected-version-mismatch", context);
        error(patch(context, draft, huge, "0"), 422, "identifier-scheme-length-range-invalid", context);
        error(patch(context, draft, "{\"validatorKey\":\"OBSOLETE\"}", "0"), 422, "invalid-identifier-scheme-configuration", context);
        assertEquals(draft, current(context, draft));
        for (String state : List.of("ACTIVE", "DEPRECATED", "RETIRED")) {
            var before = inState(context, state);
            error(patch(context, before, huge, "0"), 412, "expected-version-mismatch", context);
            error(patch(context, before, huge, before.get("version").toString()), 409,
                    state.equals("RETIRED") ? "identifier-scheme-retired" : "identifier-scheme-rules-locked", context);
            assertEquals(before, current(context, before));
        }
    }

    private Map<String, Object> inState(Context context, String state) {
        var data = create(context, uniqueCode());
        var actions = switch (state) {
            case "ACTIVE" -> List.of(IdentifierSchemeLifecycleAction.ACTIVATE);
            case "DEPRECATED" -> List.of(IdentifierSchemeLifecycleAction.ACTIVATE, IdentifierSchemeLifecycleAction.DEPRECATE);
            case "RETIRED" -> List.of(IdentifierSchemeLifecycleAction.RETIRE);
            default -> List.<IdentifierSchemeLifecycleAction>of();
        };
        long version = 0;
        for (var action : actions) {
            long expected = version++;
            RootPartyFixtures.await(() -> lifecycle.execute(new ChangeIdentifierSchemeLifecycleCommand(
                    new RequestMetadata(new TenantId(UUID.fromString(context.tenant())), context.user(), UUID.fromString(context.process())),
                    new IdentifierSchemeId(UUID.fromString((String) data.get("id"))), new IdentifierSchemeVersion(expected), action, Optional.empty())));
        }
        return current(context, data);
    }

    private static Map<String, Object> current(Context context, Map<String, Object> data) {
        return success(context.request().get(ROOT + "/" + data.get("id")), 200, context);
    }

    private static Response patch(Context context, Map<String, Object> data, String input, String version) {
        return context.request().contentType("application/json").body(input).header("If-Match", version).patch(ROOT + "/" + data.get("id"));
    }
}
