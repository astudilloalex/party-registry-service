package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies closed presence-aware identifier metadata input without evaluating resulting business validity. */
class PartyIdentifierPatchBindingTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void omissionAndExplicitNullStayDistinctForEveryMetadataField() throws Exception {
        var issuer = read("{\"issuerCode\":null}");
        assertTrue(issuer.issuerCode().isPresent()); assertNull(issuer.issuerCode().value());
        assertFalse(issuer.issuedOn().isPresent()); assertFalse(issuer.expiresOn().isPresent());
        var dates = read("{\"issuedOn\":null,\"expiresOn\":null}");
        assertFalse(dates.issuerCode().isPresent()); assertTrue(dates.issuedOn().isPresent()); assertNull(dates.issuedOn().value());
        assertTrue(dates.expiresOn().isPresent()); assertNull(dates.expiresOn().value());
    }

    @Test
    void preservesRawIssuerAndValidMonthDatesWithoutNormalizingOrCheckingIntervals() throws Exception {
        LocalDate issue = LocalDate.of(2028, Month.FEBRUARY, 29);
        LocalDate expiry = issue.minusDays(1);
        var request = read("{\"issuerCode\":\"civil_registry\",\"issuedOn\":\"" + issue + "\",\"expiresOn\":\"" + expiry + "\"}");
        assertEquals("civil_registry", request.issuerCode().value()); assertEquals(issue, request.issuedOn().value());
        assertEquals(expiry, request.expiresOn().value());
    }

    @Test
    void rejectsEveryNonmetadataDuplicateCoercedAndTrailingInput() {
        for (String body : List.of("{\"value\":\"private\"}", "{\"identifierSchemeCode\":\"OTHER\"}", "{\"status\":null}",
                "{\"isPrimary\":false}", "{\"version\":0}", "{\"createdAt\":null}", "{\"unknown\":null}",
                "{\"issuerCode\":42}", "{\"issuedOn\":true}", "{\"expiresOn\":[]}", "{\"issuedOn\":\"2026-02-30\"}",
                "{\"issuerCode\":null,\"issuerCode\":null}", "{\"issuerCode\":null,\"issuer\\u0043ode\":null}",
                "{\"issuerCode\":null} {}", "null {}", "[]", "42", "{")) {
            assertThrows(JsonProcessingException.class, () -> read(body));
        }
    }

    @Test
    void exposesNullAndEmptyBodyForRequirednessAndNonemptyPatchValidation() throws Exception {
        assertNull(read("null")); assertTrue(read("{}").empty());
        assertFalse(read("{\"issuerCode\":null}").empty());
    }

    private PartyIdentifierPatchRequest read(String body) throws JsonProcessingException {
        return json.readValue(body, PartyIdentifierPatchRequest.class);
    }
}
