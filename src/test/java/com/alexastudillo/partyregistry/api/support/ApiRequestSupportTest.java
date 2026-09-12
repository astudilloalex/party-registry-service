package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.model.request.InitialPartyIdentifierCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonCreateRequest;
import jakarta.validation.Validation;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies centralized strict parsing before commands cross into Application.
 */
class ApiRequestSupportTest {

    private final ApiRequestSupport support = new ApiRequestSupport(
            Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void validatesNestedBodies() {
        NaturalPersonCreateRequest valid = new NaturalPersonCreateRequest(
                null,
                "Ada",
                "Lovelace",
                null,
                null,
                null,
                null,
                new InitialPartyIdentifierCreateRequest(
                        "TEST_NATURAL_ACTIVE", "AB123456", null, null, null, false));
        NaturalPersonCreateRequest invalid = new NaturalPersonCreateRequest(
                null, "Ada", "Lovelace", null, null, null, null, null);

        assertEquals(valid, support.validateBody(valid));
        assertBadRequest(() -> support.validateBody(invalid));
        assertBadRequest(() -> support.validateBody(null));
    }

    @Test
    void validatesRequiredAndOptionalIdempotencyKeyCardinality() {
        StubHttpHeaders absent = new StubHttpHeaders();
        StubHttpHeaders valid = headers(ApiRequestSupport.IDEMPOTENCY_KEY_HEADER, "operation-key");
        StubHttpHeaders duplicate = headers(ApiRequestSupport.IDEMPOTENCY_KEY_HEADER, "first", "second");

        assertEquals("operation-key", support.requireIdempotencyKey(valid));
        assertTrue(support.optionalIdempotencyKey(absent).isEmpty());
        assertEquals("operation-key", support.optionalIdempotencyKey(valid).orElseThrow());
        assertBadRequest(() -> support.requireIdempotencyKey(absent));
        assertBadRequest(() -> support.optionalIdempotencyKey(duplicate));
        assertBadRequest(() -> support.requireIdempotencyKey(
                headers(ApiRequestSupport.IDEMPOTENCY_KEY_HEADER, " ")));
        assertBadRequest(() -> support.requireIdempotencyKey(
                headers(ApiRequestSupport.IDEMPOTENCY_KEY_HEADER, "x".repeat(129))));
    }

    @Test
    void parsesOnlyCanonicalPartyIdsAndNonnegativeDecimalVersions() {
        String partyId = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";

        assertEquals(UUID.fromString(partyId), support.parsePartyId(partyId).value());
        assertBadRequest(() -> support.parsePartyId(partyId.toUpperCase(Locale.ROOT)));
        assertBadRequest(() -> support.parsePartyId("not-a-uuid"));
        assertEquals(0, support.requireExpectedVersion(headers(ApiRequestSupport.IF_MATCH_HEADER, "0")).value());
        assertEquals(42, support.requireExpectedVersion(headers(ApiRequestSupport.IF_MATCH_HEADER, "42")).value());
        for (String malformed : List.of("-1", "01", "1.0", "9223372036854775808", " ")) {
            assertBadRequest(() -> support.requireExpectedVersion(
                    headers(ApiRequestSupport.IF_MATCH_HEADER, malformed)));
        }
        assertBadRequest(() -> support.requireExpectedVersion(new StubHttpHeaders()));
        assertBadRequest(() -> support.requireExpectedVersion(
                headers(ApiRequestSupport.IF_MATCH_HEADER, "1", "2")));
    }

    private static StubHttpHeaders headers(String name, String... values) {
        StubHttpHeaders headers = new StubHttpHeaders();
        for (String value : values) {
            headers.values.add(name, value);
        }
        return headers;
    }

    private static void assertBadRequest(Runnable action) {
        ApiResponseException exception = assertThrows(ApiResponseException.class, action::run);
        assertEquals("bad-request", exception.getResponseCode().getCode());
        assertEquals(400, exception.getResponseCode().getStatus());
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
