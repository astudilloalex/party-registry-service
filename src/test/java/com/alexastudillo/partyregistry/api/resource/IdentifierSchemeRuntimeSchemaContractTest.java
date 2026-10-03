package com.alexastudillo.partyregistry.api.resource;

import com.alexastudillo.partyregistry.contract.IdentifierSchemeContractValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static com.alexastudillo.partyregistry.api.resource.IdentifierSchemeHttpAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises all eight real routes and validates actual HTTP JSON against declared referenced schemas. */
@QuarkusTest
class IdentifierSchemeRuntimeSchemaContractTest {
    private final IdentifierSchemeContractValidator validator = new IdentifierSchemeContractValidator(
            IdentifierSchemeContractValidator.readContract(Path.of("docs/contracts/party-registry.openapi.yaml")));

    @Test
    void validatesEightRoutesHistoricalReplayOptionalOmissionAndAvailableNavigation() throws Exception {
        Context context = Context.fresh();
        String code = uniqueCode();
        String key = UUID.randomUUID().toString();
        String input = isolatedBody(code);
        validator.assertRequest(ROOT, HttpMethod.POST, input);
        Response created = context.request().contentType("application/json").header("Idempotency-Key", key).body(input).post(ROOT);
        check(created, ROOT, HttpMethod.POST, 201, context);
        String id = created.jsonPath().getString("data.id");
        check(context.request().get(ROOT + "/" + id), ROOT + "/{schemeId}", HttpMethod.GET, 200, context);
        check(context.request().pathParam("code", code).get(ROOT + "/by-code/{code}"), ROOT + "/by-code/{code}", HttpMethod.GET, 200, context);
        String patch = "{\"name\":\"Updated Exact Name\",\"description\":null,\"minimumLength\":1,\"maximumLength\":32767}";
        validator.assertRequest(ROOT + "/{schemeId}", HttpMethod.PATCH, patch);
        check(context.request().contentType("application/json").header("If-Match", "0").body(patch).patch(ROOT + "/" + id), ROOT + "/{schemeId}", HttpMethod.PATCH, 200, context);
        for (var action : Map.of("activate", 1, "deprecate", 2, "retire", 3).entrySet().stream().sorted(Map.Entry.comparingByValue()).toList()) {
            check(context.request().header("If-Match", action.getValue().toString()).post(ROOT + "/" + id + "/" + action.getKey()), ROOT + "/{schemeId}/" + action.getKey(), HttpMethod.POST, 200, context);
        }
        Response replay = context.request().contentType("application/json").header("Idempotency-Key", key).body(input).post(ROOT);
        check(replay, ROOT, HttpMethod.POST, 201, context);
        assertEquals(created.jsonPath().getMap("data"), replay.jsonPath().getMap("data"));
        check(context.request().queryParam("limit", "1").get(ROOT), ROOT, HttpMethod.GET, 200, context);
        Response first = context.request().queryParam("limit", "1").get(ROOT);
        String next = first.jsonPath().getString("nextCursor");
        assertNotNull(next);
        Response second = context.request().queryParam("limit", "1").queryParam("cursor", next).get(ROOT);
        check(second, ROOT, HttpMethod.GET, 200, context);
        String previous = second.jsonPath().getString("prevCursor");
        assertNotNull(previous);
        check(context.request().queryParam("limit", "1").queryParam("cursor", previous).get(ROOT), ROOT, HttpMethod.GET, 200, context);
        check(context.request().queryParam("issuingCountryCode", "QZ").queryParam("status", "RETIRED").get(ROOT), ROOT, HttpMethod.GET, 200, context);
        validator.assertSchema("IdentifierSchemeResponse", new ObjectMapper().readTree(replay.asString()).get("data"));
    }

    @Test
    void validatesActualErrorsIncludingSyntaxSemanticConflictPreconditionAndFramework() {
        Context context = Context.fresh();
        Response created = context.request().contentType("application/json").header("Idempotency-Key", UUID.randomUUID().toString())
                .body(isolatedBody(uniqueCode())).post(ROOT);
        check(created, ROOT, HttpMethod.POST, 201, context);
        String id = created.jsonPath().getString("data.id");
        check(context.request().get(ROOT + "/" + UUID.randomUUID()), ROOT + "/{schemeId}", HttpMethod.GET, 404, context);
        check(context.request().get(ROOT + "/INVALID"), ROOT + "/{schemeId}", HttpMethod.GET, 400, context);
        check(context.request().queryParam("limit", "1.1").get(ROOT), ROOT, HttpMethod.GET, 400, context);
        check(context.request().contentType("application/json").header("If-Match", "9").body("{\"name\":\"Name\"}").patch(ROOT + "/" + id), ROOT + "/{schemeId}", HttpMethod.PATCH, 412, context);
        check(context.request().contentType("application/json").header("If-Match", "0").body("{\"maximumLength\":32768}").patch(ROOT + "/" + id), ROOT + "/{schemeId}", HttpMethod.PATCH, 422, context);
        check(context.request().contentType("application/json").header("If-Match", "0").body("{\"maximumLength\":\"1\"}").patch(ROOT + "/" + id), ROOT + "/{schemeId}", HttpMethod.PATCH, 400, context);
        check(context.request().header("If-Match", "0").post(ROOT + "/" + id + "/deprecate"), ROOT + "/{schemeId}/deprecate", HttpMethod.POST, 409, context);
        check(context.request().contentType("application/json").body("{}").post(ROOT + "/" + id + "/retire"), ROOT + "/{schemeId}/retire", HttpMethod.POST, 400, context);
        check(context.request().contentType("text/plain").body("bad").post(ROOT), ROOT, HttpMethod.POST, 415, context);
        check(context.request().delete(ROOT), ROOT, HttpMethod.GET, 405, context);
        check(context.request().get(ROOT + "/" + id + "/unknown"), ROOT + "/{schemeId}", HttpMethod.GET, 404, context);
        Response invalidProcess = context.without("Process-Id").get(ROOT);
        validator.assertResponse(ROOT, HttpMethod.GET, 400, invalidProcess.asString());
        assertNull(invalidProcess.header("Process-Id"));
    }

    private void check(Response response, String path, HttpMethod method, int status, Context context) {
        assertEquals(status, response.statusCode(), response.asString());
        assertEquals(context.process(), response.header("Process-Id"));
        validator.assertResponse(path, method, status, response.asString());
    }

    private static String isolatedBody(String code) {
        return body(code).replace("\"ZZ\"", "\"QX\"");
    }
}
