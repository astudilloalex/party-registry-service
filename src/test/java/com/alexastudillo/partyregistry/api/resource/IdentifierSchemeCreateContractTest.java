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
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.alexastudillo.partyregistry.api.resource.IdentifierSchemeHttpAssertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises real creation, durable historical replay, strict decoding, and ordered failures over HTTP. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class IdentifierSchemeCreateContractTest {
    @Inject ChangeIdentifierSchemeLifecycleUseCase lifecycle;

    @Test
    void createsExactDraftWithOnlyApprovedFieldsAndMaterializedDefaults() {
        var context = Context.fresh();
        String code = " Exact-" + UUID.randomUUID() + " ";
        var data = create(context, code);
        assertEquals(REQUIRED, data.keySet());
        assertEquals(code, data.get("code"));
        assertEquals("DRAFT", data.get("status"));
        assertEquals(0, data.get("version"));
        assertEquals(false, data.get("requiresExpiration"));
        assertEquals(data.get("createdAt"), data.get("updatedAt"));
        String complete = body(uniqueCode()).replace("}", ",\"description\":\"exact description\",\"minimumLength\":32767,\"maximumLength\":32767,\"requiresExpiration\":true}");
        var optional = success(post(context, complete, "complete"), 201, context);
        assertEquals(32767, optional.get("minimumLength"));
        assertEquals(32767, optional.get("maximumLength"));
        assertEquals(true, optional.get("requiresExpiration"));
        assertEquals("exact description", optional.get("description"));
    }

    @Test
    void replayReturnsHistoricalDraftAfterLaterRetirementWithFreshCorrelationAndEffectiveDefaults() {
        var original = Context.fresh();
        String input = body(uniqueCode());
        String key = " exact-retry-key ";
        var first = success(post(original, input, key), 201, original);
        RootPartyFixtures.await(() -> lifecycle.execute(new ChangeIdentifierSchemeLifecycleCommand(
                new RequestMetadata(new TenantId(UUID.fromString(original.tenant())), original.user(), UUID.fromString(original.process())),
                new IdentifierSchemeId(UUID.fromString((String) first.get("id"))), new IdentifierSchemeVersion(0),
                IdentifierSchemeLifecycleAction.RETIRE, Optional.empty())));
        var retry = new Context(original.tenant(), "fresh-retry-user", UUID.randomUUID().toString());
        String equivalent = input.replace("}", ",\"description\":null,\"minimumLength\":null,\"maximumLength\":null,\"requiresExpiration\":false}");
        assertEquals(first, success(post(retry, equivalent, key), 201, retry));
        for (String changed : List.of(input.replace("Exact Mixed Name", "Changed"), input.replace("TRIM_UPPERCASE_V1", "OBSOLETE"),
                input.replace("}", ",\"minimumLength\":" + "9".repeat(300) + "}"))) {
            error(post(retry, changed, key), 409, "idempotency-key-conflict", retry);
        }
        var other = Context.fresh();
        error(post(other, input.replace("TRIM_UPPERCASE_V1", "OBSOLETE"), "independent"), 409, "identifier-scheme-code-conflict", other);
    }

    @Test
    void validatesBodyBeforeCreationKeyAndSemanticAdmissionAfterHeaders() {
        var context = Context.fresh();
        verifyRequestBodyValidation(context);
        String valid = body(uniqueCode());
        verifyMalformedAndSyntaxValidation(context, valid);
        verifyIdempotencyKeyValidation(context, valid);
        verifySemanticBoundsAndAdmission(context, valid);
    }

    private static void verifyRequestBodyValidation(Context context) {
        for (var entry : Map.of("", "request-body-required", "null", "request-body-required", "{}", "identifier-scheme-code-required",
                "{\"code\":null}", "identifier-scheme-code-required", "{\"code\":\" \"}", "identifier-scheme-code-required",
                "{\"code\":\"" + "x".repeat(65) + "\"}", "identifier-scheme-code-too-long").entrySet()) {
            error(context.request().contentType("application/json").body(entry.getKey()).post(ROOT), 400, entry.getValue(), context);
        }
    }

    private static void verifyMalformedAndSyntaxValidation(Context context, String valid) {
        for (String invalid : List.of("[]", "{", valid + "{}", valid.replace("OTHER", "UNKNOWN"), valid.replace("ZZ", "zz"),
                valid.replace("}", ",\"status\":\"ACTIVE\"}"), valid.replace("}", ",\"name\":\"duplicate\"}"),
                valid.replace("}", ",\"requiresExpiration\":null}"), valid.replace("}", ",\"minimumLength\":\"1\"}"))) {
            error(context.request().contentType("application/json").body(invalid).post(ROOT), 400, "bad-request", context);
        }
    }

    private static void verifyIdempotencyKeyValidation(Context context, String valid) {
        error(context.request().contentType("application/json").body(valid).post(ROOT), 400, "idempotency-key-required", context);
        error(context.request().contentType("application/json").body(valid).header("Idempotency-Key", "a", "b").post(ROOT), 400, "idempotency-key-duplicated", context);
        error(post(context, valid, " "), 400, "idempotency-key-blank", context);
        error(post(context, valid, "x".repeat(129)), 400, "idempotency-key-too-long", context);
    }

    private static void verifySemanticBoundsAndAdmission(Context context, String valid) {
        for (String bound : List.of("0", "-1", "32768", "9".repeat(300))) {
            error(post(context, valid.replace("}", ",\"minimumLength\":" + bound + "}"), UUID.randomUUID().toString()),
                    422, "identifier-scheme-length-range-invalid", context);
        }
        error(post(context, valid.replace("}", ",\"minimumLength\":20,\"maximumLength\":10}"), "incoherent"), 422, "identifier-scheme-length-range-invalid", context);
        error(post(context, valid.replace("TRIM_UPPERCASE_V1", "OBSOLETE"), "unsupported"), 422, "invalid-identifier-scheme-configuration", context);
        var created = success(post(context, valid, "unsupported"), 201, context);
        assertEquals("DRAFT", created.get("status"));
    }

    private static io.restassured.response.Response post(Context context, String body, String key) {
        return context.request().contentType("application/json").header("Idempotency-Key", key).body(body).post(ROOT);
    }
}
