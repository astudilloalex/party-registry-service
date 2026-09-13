package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.model.request.InitialPartyIdentifierCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonCreateRequest;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies centralized strict parsing before commands cross into Application.
 */
class ApiRequestSupportTest {

    private static final String PARTY_ID_INVALID = "party-id-invalid";
    private static final String GIVEN_NAMES_REQUIRED = "given-names-required";
    private static final String FAMILY_NAMES_REQUIRED = "family-names-required";
    private static final String IDENTIFIER_VALUE_REQUIRED = "identifier-value-required";
    private static final String DISPLAY_NAME_TOO_LONG = "display-name-too-long";
    private static final String BAD_REQUEST = "bad-request";
    private static final String VALID_GIVEN_NAMES = "Ada";
    private static final String VALID_FAMILY_NAMES = "Lovelace";
    private static final String VALID_SCHEME_CODE = "TEST_NATURAL_ACTIVE";
    private static final String VALID_IDENTIFIER_VALUE = "AB123456";
    private static final String VALID_TEXT = "valid";

    private final ApiRequestSupport support = new ApiRequestSupport(
            Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void validatesNestedBodies() {
        NaturalPersonCreateRequest valid = new NaturalPersonCreateRequest(
                null,
                VALID_GIVEN_NAMES,
                VALID_FAMILY_NAMES,
                null,
                null,
                null,
                null,
                new InitialPartyIdentifierCreateRequest(
                        VALID_SCHEME_CODE, VALID_IDENTIFIER_VALUE, null, null, null, false));
        NaturalPersonCreateRequest invalid = new NaturalPersonCreateRequest(
                null, VALID_GIVEN_NAMES, VALID_FAMILY_NAMES, null, null, null, null, null);

        assertEquals(valid, support.validateBody(valid));
        assertBadRequest(() -> support.validateBody(invalid), "initial-identifier-required");
        assertBadRequest(() -> support.validateBody(null), "request-body-required");
    }

    @Test
    void validatesCanonicalCopiesWithoutLosingOriginalIdempotencyInput() {
        NaturalPersonCreateRequest original = new NaturalPersonCreateRequest(
                "  Ada  ", "  ada  ", "  lovelace  ", null, null, null, " gb ",
                new InitialPartyIdentifierCreateRequest(VALID_SCHEME_CODE, VALID_IDENTIFIER_VALUE, null, null, null, false));

        assertSame(original, support.validateBody(original, NaturalPersonCreateRequest::normalizedForValidation));
        assertEquals("  ada  ", original.givenNames());
        assertEquals(" gb ", original.birthCountryCode());
        assertBadRequest(() -> support.validateBody(null, NaturalPersonCreateRequest::normalizedForValidation),
                "request-body-required");
    }

    @Test
    void distinguishesMissingInitialIdentifierSchemeAndValue() {
        assertBadRequest(() -> support.validateBody(new NaturalPersonCreateRequest(
                null, VALID_GIVEN_NAMES, VALID_FAMILY_NAMES, null, null, null, null,
                new InitialPartyIdentifierCreateRequest(null, VALID_IDENTIFIER_VALUE, null, null, null, false))),
                "identifier-scheme-code-required");
        assertBadRequest(() -> support.validateBody(new NaturalPersonCreateRequest(
                null, VALID_GIVEN_NAMES, VALID_FAMILY_NAMES, null, null, null, null,
                new InitialPartyIdentifierCreateRequest(VALID_SCHEME_CODE, null, null, null, null, false))),
                IDENTIFIER_VALUE_REQUIRED);
    }

    @Test
    void selectsMultipleViolationsByRequiredSuffixThenPathThenMessageTemplate() {
        String oversized = "long";
        assertBadRequest(() -> support.validateBody(new OrderedValidationRequest(oversized, "", "")),
                GIVEN_NAMES_REQUIRED);
        assertBadRequest(() -> support.validateBody(new OrderedValidationRequest(oversized, VALID_TEXT, "")),
                FAMILY_NAMES_REQUIRED);
        assertBadRequest(() -> support.validateBody(new OrderedValidationRequest(oversized, VALID_TEXT, VALID_TEXT)),
                DISPLAY_NAME_TOO_LONG);
    }

    @Test
    void fallsBackForUnknownAndNon400ValidationMessages() {
        assertBadRequest(() -> support.validateBody(new UnmappedValidationRequest("", VALID_TEXT)), BAD_REQUEST);
        assertBadRequest(() -> support.validateBody(new UnmappedValidationRequest(VALID_TEXT, "")), BAD_REQUEST);
    }

    @Test
    void validatesRequiredAndOptionalIdempotencyKeyCardinality() {
        StubHttpHeaders absent = new StubHttpHeaders();
        StubHttpHeaders valid = headers(ApiRequestSupport.IDEMPOTENCY_KEY_HEADER, "operation-key");
        StubHttpHeaders duplicate = headers(ApiRequestSupport.IDEMPOTENCY_KEY_HEADER, "first", "second");

        assertEquals("operation-key", support.requireIdempotencyKey(valid));
        assertTrue(support.optionalIdempotencyKey(absent).isEmpty());
        assertEquals("operation-key", support.optionalIdempotencyKey(valid).orElseThrow());
        assertBadRequest(() -> support.requireIdempotencyKey(absent), "idempotency-key-required");
        assertBadRequest(() -> support.optionalIdempotencyKey(duplicate), "idempotency-key-duplicated");
        assertBadRequest(() -> support.requireIdempotencyKey(
                headers(ApiRequestSupport.IDEMPOTENCY_KEY_HEADER, " ")), "idempotency-key-blank");
        assertBadRequest(() -> support.requireIdempotencyKey(
                headers(ApiRequestSupport.IDEMPOTENCY_KEY_HEADER, "x".repeat(129))), "idempotency-key-too-long");
    }

    @Test
    void parsesOnlyCanonicalPartyIdsAndNonnegativeDecimalVersions() {
        String partyId = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";

        assertEquals(UUID.fromString(partyId), support.parsePartyId(partyId).value());
        assertBadRequest(() -> support.parsePartyId(partyId.toUpperCase(Locale.ROOT)), PARTY_ID_INVALID);
        assertBadRequest(() -> support.parsePartyId("not-a-uuid"), PARTY_ID_INVALID);
        assertEquals(0, support.requireExpectedVersion(headers(ApiRequestSupport.IF_MATCH_HEADER, "0")).value());
        assertEquals(42, support.requireExpectedVersion(headers(ApiRequestSupport.IF_MATCH_HEADER, "42")).value());
        for (String malformed : List.of("-1", "01", "1.0", " ")) {
            assertBadRequest(() -> support.requireExpectedVersion(
                    headers(ApiRequestSupport.IF_MATCH_HEADER, malformed)), "if-match-invalid");
        }
        assertBadRequest(() -> support.requireExpectedVersion(
                headers(ApiRequestSupport.IF_MATCH_HEADER, "9223372036854775808")), "if-match-out-of-range");
        assertBadRequest(() -> support.requireExpectedVersion(new StubHttpHeaders()), "if-match-required");
        assertBadRequest(() -> support.requireExpectedVersion(
                headers(ApiRequestSupport.IF_MATCH_HEADER, "1", "2")), "if-match-duplicated");
    }

    private static StubHttpHeaders headers(String name, String... values) {
        StubHttpHeaders headers = new StubHttpHeaders();
        for (String value : values) {
            headers.values.add(name, value);
        }
        return headers;
    }

    private static void assertBadRequest(Runnable action, String expectedCode) {
        ApiResponseException exception = assertThrows(ApiResponseException.class, action::run);
        assertEquals(expectedCode, exception.getResponseCode().getCode());
        assertEquals(400, exception.getResponseCode().getStatus());
    }

    /**
     * Makes each validation ordering criterion compete with the next criterion.
     */
    private record OrderedValidationRequest(
            @Size(max = 1, message = DISPLAY_NAME_TOO_LONG) String alpha,
            @NotBlank(message = IDENTIFIER_VALUE_REQUIRED) @NotBlank(message = GIVEN_NAMES_REQUIRED) String beta,
            @NotBlank(message = FAMILY_NAMES_REQUIRED) String gamma) {
    }

    /**
     * Exercises fallback when a constraint message is not a public 400 code.
     */
    private record UnmappedValidationRequest(
            @NotBlank(message = "private-validation-detail") String unknown,
            @NotBlank(message = "conflict") String non400) {
    }

    /**
     * Supplies deterministic request headers without starting Quarkus or Docker.
     */
    private static final class StubHttpHeaders implements HttpHeaders {

        private final MultivaluedMap<String, String> values = new MultivaluedHashMap<>();

        @Override
        public List<String> getRequestHeader(String name) {
            return values.get(name);
        }

        @Override
        public String getHeaderString(String name) {
            return values.getFirst(name);
        }

        @Override
        public MultivaluedMap<String, String> getRequestHeaders() {
            return values;
        }

        @Override
        public List<MediaType> getAcceptableMediaTypes() {
            return List.of();
        }

        @Override
        public List<Locale> getAcceptableLanguages() {
            return List.of();
        }

        @Override
        public MediaType getMediaType() {
            return null;
        }

        @Override
        public Locale getLanguage() {
            return null;
        }

        @Override
        public Map<String, Cookie> getCookies() {
            return Map.of();
        }

        @Override
        public Date getDate() {
            return null;
        }

        @Override
        public int getLength() {
            return -1;
        }
    }
}
