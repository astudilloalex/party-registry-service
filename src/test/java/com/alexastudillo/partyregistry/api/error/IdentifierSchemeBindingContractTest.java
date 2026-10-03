package com.alexastudillo.partyregistry.api.error;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies real asynchronous buffered body handling and strictly sanitized typed binding failures. */
@QuarkusTest
class IdentifierSchemeBindingContractTest {
    private static final String ROOT = "/v1/identifier-scheme-binding-verification";
    private static final String ID = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String CREATE = """
            {"code":"Exact","issuingCountryCode":"EC","category":"OTHER","applicableSubjectType":"BOTH",
             "name":"name","normalizerKey":"historical","validatorKey":"unknown"}
            """;

    @Test
    void missingAndNullBodiesPrecedeOperationHeaders() {
        for (String path : new String[]{"/create", "/patch/bad"}) {
            for (String body : new String[]{"", "null"}) {
                error(path, body, "request-body-required");
            }
        }
        error("/create", "{}", "identifier-scheme-code-required");
        error("/patch/bad", "{}", "patch-property-required");
        error("/create", CREATE.replace("\"Exact\"", "null"), "identifier-scheme-code-required");
        error("/create", CREATE.replace("Exact", "😀".repeat(65)), "identifier-scheme-code-too-long");
        error("/patch/bad", "{\"name\":null}", "bad-request");
    }

    @Test
    void malformedTypedBodiesUseOnlyTheSharedStatusCodeEnvelope() {
        for (String body : new String[]{"{", "[]", "7", "{\"code\":true}", "{\"code\":\"a\",\"code\":\"b\"}",
                "{\"extra\":null}", CREATE + " {}", "{\"requiresExpiration\":null}",
                CREATE.replace("\"OTHER\"", "\"other\"")}) {
            error("/create", body, "bad-request");
        }
        for (String body : new String[]{"{", "[]", "{\"name\":42}", "{\"minimumLength\":1.0}",
                "{\"description\":null,\"description\":null}", "{\"name\":\"valid\",\"status\":\"ACTIVE\"}"}) {
            error("/patch/bad", body, "bad-request");
        }
    }

    @Test
    void bufferedByteArrayRejectsEveryNonemptyLifecycleBodyOnTheEventLoop() {
        for (byte[] body : new byte[][]{"{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                " \r\n\t".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "null".getBytes(java.nio.charset.StandardCharsets.UTF_8), new byte[]{0}, new byte[]{(byte) 0xff}}) {
            var response = request().body(body).post(ROOT + "/lifecycle/bad");
            assertEquals(400, response.statusCode());
            assertEquals(Map.of("status", 400, "code", "bad-request"), response.jsonPath().getMap("$"));
        }
        var accepted = request().header("If-Match", "0").body(new byte[0]).post(ROOT + "/lifecycle/" + ID);
        assertEquals(200, accepted.statusCode());
        assertEquals(true, accepted.jsonPath().getBoolean("data.eventLoop"));
        assertEquals(200, accepted.jsonPath().getInt("status"));
        assertEquals("successful", accepted.jsonPath().getString("code"));
        error("/lifecycle/bad", "", "identifier-scheme-id-invalid");
    }

    @Test
    void actualTypedReaderRetainsHugeIntegerAndPatchPresenceOnTheEventLoop() {
        String huge = "9".repeat(300);
        String body = CREATE.stripTrailing();
        body = body.substring(0, body.length() - 1) + ",\"minimumLength\":" + huge + '}';
        var create = request().header("Idempotency-Key", " Exact Key ").body(body).post(ROOT + "/create");
        assertEquals(201, create.statusCode());
        assertEquals(201, create.jsonPath().getInt("status"));
        assertEquals("successful", create.jsonPath().getString("code"));
        assertEquals(huge, create.jsonPath().getString("data.minimumLength"));
        assertEquals(true, create.jsonPath().getBoolean("data.eventLoop"));
        var patch = request().header("If-Match", "0").body("{\"minimumLength\":null}").post(ROOT + "/patch/" + ID);
        assertEquals(200, patch.statusCode());
        assertEquals(true, patch.jsonPath().getBoolean("data.minimumPresent"));
        assertEquals(false, patch.jsonPath().getBoolean("data.namePresent"));
        assertEquals(true, patch.jsonPath().getBoolean("data.eventLoop"));
    }

    private static void error(String path, String body, String code) {
        var response = request().body(body).post(ROOT + path);
        assertEquals(400, response.statusCode(), body);
        assertEquals(ID, response.header("Process-Id"));
        assertEquals(Map.of("status", 400, "code", code), response.jsonPath().getMap("$"), body);
    }

    private static RequestSpecification request() {
        return given().contentType("application/json").header("Process-Id", ID)
                .header("Tenant-Id", ID).header("User-Id", "boundary-tester");
    }
}
