package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.partyregistry.api.filter.RequestContextFilter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Verifies exact shared root error envelopes, distinct version codes, and preservation of unexpected or cancellation signals. */
@QuarkusTest
@TestProfile(GlobalErrorContractTestProfile.class)
class RootErrorContractTest {

    private static final String TENANT = "0198ce2b-d6a3-7d6e-80ba-d97b21d793e5";
    private static final String PROCESS = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";

    @Test
    void emitsExactlyTheDeclaredStatusAndCodeForEveryRootFailure() {
        Map<String, PartyResponseCode> scenarios = Map.of(
                "required", PartyResponseCode.DISPLAY_NAME_REQUIRED,
                "blank", PartyResponseCode.BLANK_DISPLAY_NAME,
                "too-long", PartyResponseCode.DISPLAY_NAME_TOO_LONG,
                "cursor", PartyResponseCode.BAD_REQUEST,
                "missing", PartyResponseCode.PARTY_NOT_FOUND,
                "patch-version", PartyResponseCode.EXPECTED_VERSION_MISMATCH,
                "lifecycle-version", PartyResponseCode.STALE_PARTY_VERSION,
                "key-conflict", PartyResponseCode.IDEMPOTENCY_KEY_CONFLICT,
                "lifecycle", PartyResponseCode.INVALID_PARTY_LIFECYCLE,
                "evidence", PartyResponseCode.MISSING_QUALIFYING_IDENTIFIER);
        scenarios.forEach((scenario, code) -> assertError(scenario, code.getStatus(), code.getCode()));
        assertEquals(400, PartyResponseCode.DISPLAY_NAME_REQUIRED.getStatus());
        assertEquals("display-name-required", PartyResponseCode.DISPLAY_NAME_REQUIRED.getCode());
        assertEquals(412, PartyResponseCode.EXPECTED_VERSION_MISMATCH.getStatus());
        assertEquals(412, PartyResponseCode.STALE_PARTY_VERSION.getStatus());
    }

    @Test
    void preservesUnknownAndCancellationFailuresForTheExistingGlobalHandling() {
        var translator = new PartyApiErrorTranslator();
        for (Throwable failure : List.of(new CancellationException("cancel"), new IllegalStateException("internal"))) {
            assertSame(failure, translator.translate(failure));
        }
        assertError("unexpected", 500, "server-error");
    }

    private static void assertError(String scenario, int status, String code) {
        var response = given().header(RequestContextFilter.TENANT_ID_HEADER, TENANT)
                .header(RequestContextFilter.PROCESS_ID_HEADER, PROCESS).header(RequestContextFilter.USER_ID_HEADER, "error-tester")
                .get("/v1/root-error-verification/" + scenario);
        assertEquals(status, response.statusCode());
        assertEquals(PROCESS, response.header(RequestContextFilter.PROCESS_ID_HEADER));
        Map<String, Object> body = response.jsonPath().getMap("$");
        assertEquals(Set.of("status", "code"), body.keySet());
        assertEquals(Map.of("status", status, "code", code), body);
    }
}
