package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierPatchRequest;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import jakarta.validation.Validation;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins PATCH body/path/version precedence, exact typed version bounds and complete ignorance of replay headers. */
class PartyIdentifierPatchRequestSupportTest {
    private static final String PARTY = "0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1";
    private static final String IDENTIFIER = "0198d15c-0557-7a16-a6a8-e3f0ddb5b744";
    private static final RequestMetadata METADATA = new RequestMetadata(new TenantId(UUID.randomUUID()), "metadata-operator", UUID.randomUUID());

    @Test
    void versionHeaderProducesOnlyIndependentIdentifierVersionsIncludingMaximum() {
        withSupport(support -> {
            assertEquals(new PartyIdentifierVersion(0), support.identifierVersion(headers("0")));
            assertEquals(new PartyIdentifierVersion(Long.MAX_VALUE), support.identifierVersion(headers(Long.toString(Long.MAX_VALUE))));
            code("if-match-required", () -> support.identifierVersion(headers()));
            code("if-match-duplicated", () -> support.identifierVersion(headers("0", "0")));
            for (String value : List.of("", " ", "01", "-1", "+1", "*", "\"0\"", " 0", "0 ", "1.0", "private-version")) {
                var header = headers(value);
                code("if-match-invalid", () -> support.identifierVersion(header));
            }
            var overflow = headers("9223372036854775808");
            code("if-match-out-of-range", () -> support.identifierVersion(overflow));
        });
    }

    @Test
    void bodyRequirednessEmptyPatchAndRawIssuerRulesPrecedeSelectorsAndVersion() {
        withSupport(support -> {
            var empty = new PartyIdentifierPatchRequest(FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent());
            var large = issuer("😀".repeat(65));
            var invalid = issuer("invalid__code");
            var missingHeader = headers();
            code("request-body-required", () -> support.patchCommand(METADATA, "bad-party", "bad-identifier", null, missingHeader));
            code("patch-property-required", () -> support.patchCommand(METADATA, "bad-party", "bad-identifier", empty, missingHeader));
            code("issuer-code-too-long", () -> support.patchCommand(METADATA, "bad-party", "bad-identifier", large, missingHeader));
            code("issuer-code-invalid", () -> support.patchCommand(METADATA, "bad-party", "bad-identifier", invalid, missingHeader));
            var valid = issuer(null);
            code("party-id-invalid", () -> support.patchCommand(METADATA, "bad-party", "bad-identifier", valid, missingHeader));
            code("bad-request", () -> support.patchCommand(METADATA, PARTY, "bad-identifier", valid, missingHeader));
            code("if-match-required", () -> support.patchCommand(METADATA, PARTY, IDENTIFIER, valid, missingHeader));
        });
    }

    @Test
    void mapsPresenceAndOriginalInputWithoutPartyVersionOrReplayHeaderValidation() {
        withSupport(support -> {
            LocalDate date = LocalDate.of(2028, Month.FEBRUARY, 29);
            var body = new PartyIdentifierPatchRequest(FieldUpdate.present("civil_registry"), FieldUpdate.present(date), FieldUpdate.absent());
            var header = headers("7"); header.values().addAll("Idempotency-Key", "", "private-key");
            var command = support.patchCommand(METADATA, PARTY, IDENTIFIER, body, header);
            assertSame(METADATA, command.requestMetadata()); assertEquals(UUID.fromString(PARTY), command.partyId().value());
            assertEquals(UUID.fromString(IDENTIFIER), command.identifierId().value()); assertEquals(new PartyIdentifierVersion(7), command.expectedVersion());
            assertEquals("civil_registry", command.issuerCode().value()); assertEquals(date, command.issuedOn().value());
            assertFalse(command.expiresOn().isPresent()); assertFalse(command.toString().contains("civil_registry"));
        });
    }

    @Test
    void suppliedNullAndBoundedBlankIssuerStayPresentWithoutValidatingHistoricalOmission() {
        withSupport(support -> {
            for (String value : new String[]{null, "", " ", "\u2003".repeat(64), "A".repeat(64)}) {
                var command = support.patchCommand(METADATA, PARTY, IDENTIFIER, issuer(value), headers("0"));
                assertTrue(command.issuerCode().isPresent()); assertEquals(value, command.issuerCode().value());
            }
            var dateOnly = new PartyIdentifierPatchRequest(FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.present(null));
            var command = support.patchCommand(METADATA, PARTY, IDENTIFIER, dateOnly, headers("0"));
            assertFalse(command.issuerCode().isPresent()); assertTrue(command.expiresOn().isPresent()); assertNull(command.expiresOn().value());
        });
    }

    private static PartyIdentifierPatchRequest issuer(String value) {
        return new PartyIdentifierPatchRequest(FieldUpdate.present(value), FieldUpdate.absent(), FieldUpdate.absent());
    }

    private static void withSupport(Consumer<PartyIdentifierRequestSupport> test) {
        try (var factory = Validation.buildDefaultValidatorFactory()) { test.accept(new PartyIdentifierRequestSupport(new ApiRequestSupport(factory.getValidator()))); }
    }

    private static void code(String expected, Runnable action) {
        var failure = assertThrows(ApiResponseException.class, action::run);
        assertEquals(400, failure.getResponseCode().getStatus()); assertEquals(expected, failure.getResponseCode().getCode());
        assertFalse(failure.toString().contains("private-version"));
    }

    private static Headers headers(String... values) {
        var map = new MultivaluedHashMap<String, String>(); map.put("If-Match", List.of(values)); return new Headers(map);
    }

    /** Supplies only raw header access for ordered validation and deliberately invalid ignored replay headers. */
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
