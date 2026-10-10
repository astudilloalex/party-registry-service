package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.support.ApiRequestSupport;
import com.alexastudillo.partyregistry.api.support.PartyIdentifierActionBodies;
import com.alexastudillo.partyregistry.api.support.PartyIdentifierRequestSupport;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins strict optional replacement syntax, exact false defaults and ordered identifier action command mapping. */
class PartyIdentifierPrimaryBindingTest {
    private static final String PARTY = UUID.randomUUID().toString();
    private static final String IDENTIFIER = UUID.randomUUID().toString();
    private static final RequestMetadata METADATA = new RequestMetadata(new TenantId(UUID.randomUUID()), "primary-operator", UUID.randomUUID());
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void absentBytesEmptyObjectOmittedPropertyAndExplicitFalseShareOneDefault() throws Exception {
        var absent = PartyIdentifierActionBodies.primary(null, json);
        assertFalse(absent.replaceCurrentPrimary());
        assertEquals(absent, PartyIdentifierActionBodies.primary(new byte[0], json));
        assertEquals(absent, read("{}")); assertEquals(absent, read("{\"replaceCurrentPrimary\":false}"));
        assertTrue(read("{\"replaceCurrentPrimary\":true}").replaceCurrentPrimary());
    }

    @Test
    void literalOrPropertyNullAndScalarCoercionNeverAcquireTheDefault() {
        for (String body : List.of("null", "[]", "true", "42", "{\"replaceCurrentPrimary\":null}",
                "{\"replaceCurrentPrimary\":\"true\"}", "{\"replaceCurrentPrimary\":1}", "{\"replaceCurrentPrimary\":{}}")) {
            assertThrows(JsonProcessingException.class, () -> read(body));
        }
    }

    @Test
    void duplicateEscapedAliasesUnknownFieldsAndTrailingTokensAreRejected() {
        for (String body : List.of("{\"replaceCurrentPrimary\":true,\"replaceCurrentPrimary\":false}",
                "{\"replaceCurrentPrimary\":false,\"replaceCurrentPrimar\\u0079\":true}", "{\"extra\":false}", "{} {}", "null {}", "{")) {
            assertThrows(JsonProcessingException.class, () -> read(body));
        }
    }

    @Test
    void bufferedWhitespaceAndMalformedInputUseOnlySafeBadRequest() {
        for (String text : List.of(" \r\n\t", "null", "[]", "{", "{\"replaceCurrentPrimary\":null}", "{} {}")) {
            byte[] body = text.getBytes(StandardCharsets.UTF_8);
            var failure = assertThrows(ApiResponseException.class, () -> PartyIdentifierActionBodies.primary(body, json));
            assertEquals("bad-request", failure.getResponseCode().getCode()); assertEquals(400, failure.getResponseCode().getStatus());
        }
    }

    @Test
    void effectiveReplacementAndExactOptionalKeyMapToIndependentIdentifierVersion() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var support = new PartyIdentifierRequestSupport(new ApiRequestSupport(factory.getValidator()));
            var headers = headers(Long.toString(Long.MAX_VALUE)); headers.values().add("Idempotency-Key", " exact-key ");
            var command = support.primaryCommand(METADATA, PARTY, IDENTIFIER, new PartyIdentifierPrimaryRequest(true), headers);
            assertTrue(command.replaceCurrentPrimary()); assertEquals(" exact-key ", command.idempotencyKey());
            assertEquals(Long.MAX_VALUE, command.expectedVersion().value()); assertEquals(UUID.fromString(PARTY), command.partyId().value());
            assertEquals(UUID.fromString(IDENTIFIER), command.identifierId().value());
            assertEquals(command.effectiveRequest().replaceCurrentPrimary(), command.replaceCurrentPrimary());
            assertFalse(support.primaryCommand(METADATA, PARTY, IDENTIFIER, null, headers("0")).replaceCurrentPrimary());
        }
    }

    @Test
    void selectorsThenVersionThenOptionalKeyDetermineFirstSyntaxFailure() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var support = new PartyIdentifierRequestSupport(new ApiRequestSupport(factory.getValidator()));
            var headers = headers(); headers.values().addAll("Idempotency-Key", "", "duplicate");
            var body = new PartyIdentifierPrimaryRequest(false);
            assertCode("party-id-invalid", () -> support.primaryCommand(METADATA, "bad-party", "bad-identifier", body, headers));
            assertCode("bad-request", () -> support.primaryCommand(METADATA, PARTY, "bad-identifier", body, headers));
            assertCode("if-match-required", () -> support.primaryCommand(METADATA, PARTY, IDENTIFIER, body, headers));
            headers.values().add("If-Match", "0");
            assertCode("idempotency-key-duplicated", () -> support.primaryCommand(METADATA, PARTY, IDENTIFIER, body, headers));
        }
    }

    private PartyIdentifierPrimaryRequest read(String body) throws JsonProcessingException { return json.readValue(body, PartyIdentifierPrimaryRequest.class); }
    private static void assertCode(String code, Runnable action) {
        assertEquals(code, assertThrows(ApiResponseException.class, action::run).getResponseCode().getCode());
    }
    private static Headers headers(String... versions) {
        var values = new MultivaluedHashMap<String, String>(); values.put("If-Match", new ArrayList<>(List.of(versions))); return new Headers(values);
    }
    /** Exposes only raw header values needed to verify exact action parsing and precedence. */
    private record Headers(MultivaluedMap<String, String> values) implements HttpHeaders {
        @Override public List<String> getRequestHeader(String name) { return values.get(name); }
        @Override public String getHeaderString(String name) { return values.getFirst(name); }
        @Override public MultivaluedMap<String, String> getRequestHeaders() { return values; }
        @Override public List<MediaType> getAcceptableMediaTypes() { return List.of(); }
        @Override public List<Locale> getAcceptableLanguages() { return List.of(); }
        @Override public MediaType getMediaType() { return null; }
        @Override public Locale getLanguage() { return null; }
        @Override public Map<String, Cookie> getCookies() { return Map.of(); }
        @Override public Date getDate() { return null; }
        @Override public int getLength() { return -1; }
    }
}
