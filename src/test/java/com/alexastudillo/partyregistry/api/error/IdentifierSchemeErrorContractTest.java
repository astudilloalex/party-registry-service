package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies every declared scheme failure against the real shared error boundary, including business precedence. */
@QuarkusTest
class IdentifierSchemeErrorContractTest {
    private static final String ROOT = "/v1/identifier-scheme-error-verification";
    private static final String PROCESS = IdentifierSchemeErrorVerificationResource.ID;

    @Test
    void allTenNeutralFailuresAndSharedKeyDependencyFailuresHaveExactStatusesAndCodes() {
        Map<String, PartyResponseCode> cases = Map.ofEntries(
                Map.entry("missing", PartyResponseCode.IDENTIFIER_SCHEME_NOT_FOUND),
                Map.entry("code-conflict", PartyResponseCode.IDENTIFIER_SCHEME_CODE_CONFLICT),
                Map.entry("version", PartyResponseCode.EXPECTED_VERSION_MISMATCH),
                Map.entry("locked", PartyResponseCode.IDENTIFIER_SCHEME_RULES_LOCKED),
                Map.entry("retired", PartyResponseCode.IDENTIFIER_SCHEME_RETIRED),
                Map.entry("lifecycle", PartyResponseCode.INVALID_IDENTIFIER_SCHEME_LIFECYCLE),
                Map.entry("exhausted", PartyResponseCode.IDENTIFIER_SCHEME_VERSION_EXHAUSTED),
                Map.entry("range", PartyResponseCode.IDENTIFIER_SCHEME_LENGTH_RANGE_INVALID),
                Map.entry("configuration", PartyResponseCode.INVALID_IDENTIFIER_SCHEME_CONFIGURATION),
                Map.entry("cursor", PartyResponseCode.BAD_REQUEST),
                Map.entry("key", PartyResponseCode.IDEMPOTENCY_KEY_CONFLICT),
                Map.entry("dependency", PartyResponseCode.DEPENDENCY_UNAVAILABLE));
        cases.forEach((scenario, expected) -> {
            var source = IdentifierSchemeErrorVerificationResource.failureFor(scenario);
            var translated = assertInstanceOf(ApiResponseException.class, new PartyApiErrorTranslator().translate(source));
            assertSame(expected, translated.getResponseCode());
            assertSame(source, translated.getCause());
            error(request().get(ROOT + '/' + scenario), expected.getStatus(), expected.getCode());
        });
        assertEquals("identifier-scheme-id-invalid", PartyResponseCode.IDENTIFIER_SCHEME_ID_INVALID.getCode());
        assertEquals(400, PartyResponseCode.IDENTIFIER_SCHEME_ID_INVALID.getStatus());
        assertEquals(201, PartyResponseCode.CREATED.getStatus());
        assertEquals("successful", PartyResponseCode.CREATED.getCode());
    }

    @Test
    void unknownCorruptedAndCancellationFailuresKeepIdentityAndUnexpectedHttpIsSanitized() {
        var translator = new PartyApiErrorTranslator();
        for (Throwable failure : List.of(new CancellationException("private cancellation"),
                IdentifierSchemeErrorVerificationResource.failureFor("unexpected"),
                IdentifierSchemeErrorVerificationResource.failureFor("corruption"))) {
            assertSame(failure, translator.translate(failure));
        }
        error(request().get(ROOT + "/unexpected"), 500, "server-error");
        error(request().get(ROOT + "/corruption"), 500, "server-error");
    }

    @Test
    void hugeIntegralCreationBoundsReachRealApplication422AfterCodeUniqueness() {
        String body = """
                {"code":"Exact","issuingCountryCode":"EC","category":"OTHER","applicableSubjectType":"BOTH",
                 "name":"name","normalizerKey":"TRIM_UPPERCASE_V1","validatorKey":"ALPHANUMERIC_V1",
                 "minimumLength":%s}
                """.formatted("9".repeat(300));
        error(request().header("Idempotency-Key", "key").body(body).post(ROOT + "/create/new"),
                422, "identifier-scheme-length-range-invalid");
        error(request().header("Idempotency-Key", "key").body(body).post(ROOT + "/create/code-conflict"),
                409, "identifier-scheme-code-conflict");
        error(request().header("Idempotency-Key", "key").body(body.replace("TRIM_UPPERCASE_V1", "unknown"))
                .post(ROOT + "/create/new"), 422, "identifier-scheme-length-range-invalid");
        error(request().header("Idempotency-Key", "key").body(body.replace("9".repeat(300), "1")
                .replace("TRIM_UPPERCASE_V1", "unknown")).post(ROOT + "/create/new"),
                422, "invalid-identifier-scheme-configuration");
    }

    @Test
    void hugePatchBoundsPreserveAbsenceVersionStateExhaustionBeforeApplicationRangeFailure() {
        String body = "{\"minimumLength\":" + "9".repeat(300) + '}';
        error(request().header("If-Match", "1").body(body).post(ROOT + "/patch/absent"), 404, "identifier-scheme-not-found");
        error(request().header("If-Match", "0").body(body).post(ROOT + "/patch/stale"), 412, "expected-version-mismatch");
        error(request().header("If-Match", "1").body(body).post(ROOT + "/patch/locked"), 409, "identifier-scheme-rules-locked");
        error(request().header("If-Match", "1").body(body).post(ROOT + "/patch/retired"), 409, "identifier-scheme-retired");
        error(request().header("If-Match", Long.toString(Long.MAX_VALUE)).body(body).post(ROOT + "/patch/exhausted"),
                409, "identifier-scheme-version-exhausted");
        error(request().header("If-Match", "1").body(body).post(ROOT + "/patch/range"),
                422, "identifier-scheme-length-range-invalid");
    }

    private static RequestSpecification request() {
        return given().contentType("application/json").header("Process-Id", PROCESS)
                .header("Tenant-Id", PROCESS).header("User-Id", "boundary-tester");
    }

    private static void error(Response response, int status, String code) {
        assertEquals(status, response.statusCode());
        assertEquals(PROCESS, response.header("Process-Id"));
        assertEquals(Map.of("status", status, "code", code), response.jsonPath().getMap("$"));
    }
}
