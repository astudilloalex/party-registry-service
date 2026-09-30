package com.alexastudillo.partyregistry.api.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies actionable, correlated create-rejection logs without disclosing
 * request values.
 */
@QuarkusTest
class NaturalPersonRejectionLoggingContractTest {

    private static final String SECRET_KEY = "SecretKey";
    private static final String SENSITIVE_IDENTIFIER_VALUE = "SensitiveIdentifierValue";
    private static final String SCHEME_PLACEHOLDER = "{{initialIdentifierSchemeCode}}";
    private static final String PRIMARY_PROPERTY = "\"isPrimary\": true";
    private static final String BAD_REQUEST = "bad-request";
    private static final String VALID_BODY = """
            {
              "givenNames": "SensitiveGivenName",
              "familyNames": "SensitiveFamilyName",
              "initialIdentifier": {
                "identifierSchemeCode": "{{initialIdentifierSchemeCode}}",
                "value": "SensitiveIdentifierValue",
                "isPrimary": true
              }
            }
            """;

    @Test
    void explainsMissingIdempotencyKey() {
        assertRejectionLog(VALID_BODY, List.of(),
                "idempotency-key-required",
                "source=request-validation field=Idempotency-Key rule=idempotency-key-required");
    }

    @Test
    void explainsDuplicateBlankAndOversizedIdempotencyKeys() {
        assertRejectionLog(VALID_BODY, List.of("SecretKeyOne", "SecretKeyTwo"),
                "idempotency-key-duplicated",
                "source=request-validation field=Idempotency-Key rule=idempotency-key-duplicated");
        assertRejectionLog(VALID_BODY, List.of(" "),
                "idempotency-key-blank",
                "source=request-validation field=Idempotency-Key rule=idempotency-key-blank");
        assertRejectionLog(VALID_BODY, List.of(SECRET_KEY.repeat(20)),
                "idempotency-key-too-long",
                "source=request-validation field=Idempotency-Key rule=idempotency-key-too-long");
    }

    @Test
    void explainsNestedBodyConstraintWithoutLoggingRejectedValue() {
        assertRejectionLog(
                VALID_BODY.replace(SENSITIVE_IDENTIFIER_VALUE, SENSITIVE_IDENTIFIER_VALUE.repeat(20)),
                List.of(SECRET_KEY),
                "identifier-value-too-long",
                "source=validateBody model=NaturalPersonCreateRequest field=initialIdentifier.value rule=identifier-value-too-long");
    }

    @Test
    void distinguishesMissingInitialIdentifierSchemeAndValue() {
        assertRejectionLog(VALID_BODY.replace("\"identifierSchemeCode\": \"" + SCHEME_PLACEHOLDER + "\",", ""),
                List.of(SECRET_KEY),
                "identifier-scheme-code-required",
                "source=validateBody model=NaturalPersonCreateRequest field=initialIdentifier.identifierSchemeCode rule=identifier-scheme-code-required");
        assertRejectionLog(VALID_BODY.replace("\"value\": \"" + SENSITIVE_IDENTIFIER_VALUE + "\",", ""),
                List.of(SECRET_KEY),
                "identifier-value-required",
                "source=validateBody model=NaturalPersonCreateRequest field=initialIdentifier.value rule=identifier-value-required");
    }

    @Test
    void selectsRequiredViolationBeforeAlphabeticallyEarlierOptionalViolation() {
        String body = VALID_BODY.replace("SensitiveGivenName", "")
                .replace("SensitiveFamilyName", "")
                .replace("\"givenNames\":", "\"birthCountryCode\": \"invalid\", \"givenNames\":");
        assertRejectionLog(body, List.of(SECRET_KEY),
                "family-names-required",
                "source=validateBody model=NaturalPersonCreateRequest field=familyNames rule=family-names-required");
    }

    @Test
    void explainsMissingBody() {
        assertRejectionLog("null", List.of(SECRET_KEY), "request-body-required",
                "source=request-validation field=body rule=request-body-required");
    }

    @Test
    void explainsJsonBindingFailureWithoutLoggingExceptionMessage() {
        assertRejectionLog(VALID_BODY.replace(PRIMARY_PROPERTY,
                "\"issuedOn\": \"SensitiveInvalidDate\", \"isPrimary\": true"),
                List.of(SECRET_KEY), BAD_REQUEST,
                "source=json-binding resource=NaturalPersonResource field=body.initialIdentifier.issuedOn rule=invalid-json-value");
    }

    @Test
    void redactsUnknownJsonPropertyNames() {
        assertRejectionLog(VALID_BODY.replace(PRIMARY_PROPERTY,
                "\"SensitiveUnknownProperty\": true, \"isPrimary\": true"),
                List.of(SECRET_KEY), BAD_REQUEST,
                "source=json-binding resource=NaturalPersonResource field=body.initialIdentifier.<unknown> rule=unknown-property");
    }

    private static void assertRejectionLog(
            String body, List<String> keys, String expectedCode, String expectedDiagnostic) {
        String processId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        List<CapturedLog> captured = new CopyOnWriteArrayList<>();
        Logger root = Logger.getLogger("");
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                if (logRecord instanceof ExtLogRecord extended
                        && processId.equals(extended.getMdc("processId"))) {
                    captured.add(new CapturedLog(new SimpleFormatter().formatMessage(extended),
                            extended.getMdc("tenantId"), extended.getMdc("userId"),
                            extended.getThrown()));
                }
            }

            @Override
            public void flush() {
                //
            }

            @Override
            public void close() {
                //
            }
        };
        root.addHandler(handler);
        try {
            var request = given().contentType(ContentType.JSON)
                    .header("Tenant-Id", tenantId)
                    .header("User-Id", "diagnostic-test")
                    .header("Process-Id", processId);
            for (String key : keys) {
                request.header("Idempotency-Key", key);
            }
            var response = request.body(body).post("/v1/natural-person");
            assertEquals(400, response.statusCode());
            assertEquals(processId, response.header("Process-Id"));
            assertEquals(Map.of("status", 400, "code", expectedCode), response.jsonPath().getMap("$"));
        } finally {
            root.removeHandler(handler);
            handler.close();
        }
        for (CapturedLog log : captured) {
            assertEquals(tenantId, log.tenantId());
            assertEquals("diagnostic-test", log.userId());
            assertNull(log.failure());
        }
        List<String> logs = captured.stream().map(CapturedLog::message).toList();
        assertTrue(logs.stream().anyMatch(log -> log.contains(
                "Request rejected status=400 code=" + expectedCode + " " + expectedDiagnostic)),
                logs::toString);
        assertTrue(logs.stream().anyMatch(log -> log.contains(
                "Request completed operation=create status=400 code=" + expectedCode + " ")));
        String combined = String.join("\n", logs);
        assertFalse(combined.contains("Sensitive"), combined);
        assertFalse(combined.contains(SECRET_KEY), combined);
        assertFalse(combined.contains(SCHEME_PLACEHOLDER), combined);
    }

    /**
     * Snapshots request MDC before the response filter clears its owned context.
     */
    private record CapturedLog(String message, String tenantId, String userId, Throwable failure) {
    }
}
