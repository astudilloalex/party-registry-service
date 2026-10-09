package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Characterizes strict identifier creation tokens, nullable defaults and original raw fingerprint input. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class PartyIdentifierCreateBindingTest {
    private static final String VALID = "{\"identifierSchemeCode\":\"TEST_NATURAL_ACTIVE\",\"value\":\"  ab1234  \"}";
    @Inject ObjectMapper json;

    @Test
    void rejectsCoercedScalarsAndNullPrimary() {
        for (String body : List.of("{\"identifierSchemeCode\":42,\"value\":\"ab1234\"}",
                "{\"identifierSchemeCode\":\"TEST_NATURAL_ACTIVE\",\"value\":42}",
                with("\"issuerCode\":true"), with("\"issuedOn\":42"), with("\"expiresOn\":[]"),
                with("\"isPrimary\":\"false\""), with("\"isPrimary\":0"), with("\"isPrimary\":null"))) {
            assertThrows(JsonProcessingException.class, () -> read(body));
        }
    }

    @Test
    void rejectsEveryDuplicateAndUndeclaredProperty() {
        for (String body : List.of(with("\"value\":\"different\""), with("\"va\\u006cue\":\"different\""),
                with("\"issuerCode\":null,\"issuerCode\":null"), with("\"isPrimary\":false,\"isPrimary\":false"),
                with("\"status\":\"VERIFIED\""), with("\"version\":0"), with("\"unknown\":null"))) {
            assertThrows(JsonProcessingException.class, () -> read(body));
        }
    }

    @Test
    void rejectsTrailingContentAndNonobjectBodies() {
        for (String body : List.of(VALID + " {}", VALID + " null", "null {}", "[]", "42", "\"object\"", "{")) {
            assertThrows(JsonProcessingException.class, () -> read(body));
        }
    }

    @Test
    void validatesCalendarTokensWithoutDateCoercionOrLenientRepair() {
        for (String date : List.of("2026-02-30", "2026-2-01", "2026-01-01T00:00:00Z", " 2026-01-01", "2026-01-01 ")) {
            String body = with("\"issuedOn\":\"" + date + "\"");
            assertThrows(JsonProcessingException.class, () -> read(body));
        }
        LocalDate leap = LocalDate.of(2028, Month.FEBRUARY, 29);
        String body = with("\"issuedOn\":\"" + leap + "\",\"expiresOn\":null");
        assertEquals(leap, readUnchecked(body).issuedOn());
    }

    @Test
    void preservesNullableAbsenceAndOriginalStringsWithFalseDefault() throws Exception {
        var request = read(VALID);
        assertEquals("TEST_NATURAL_ACTIVE", request.identifierSchemeCode()); assertEquals("  ab1234  ", request.value());
        assertNull(request.issuerCode()); assertNull(request.issuedOn()); assertNull(request.expiresOn()); assertFalse(request.isPrimary());
        assertEquals(request, read(with("\"issuerCode\":null,\"issuedOn\":null,\"expiresOn\":null,\"isPrimary\":false")));
        assertEquals("civil_registry", read(with("\"issuerCode\":\"civil_registry\"")).issuerCode());
    }

    @Test
    void preservesMissingAndNullRequiredFieldsForOrderedBeanValidation() throws Exception {
        assertNull(read("{}").identifierSchemeCode()); assertNull(read("{}").value());
        assertNull(read("{\"identifierSchemeCode\":null,\"value\":null}").value());
        assertNull(read("null"));
    }

    private PartyIdentifierCreateRequest read(String body) throws JsonProcessingException {
        return json.readValue(body, PartyIdentifierCreateRequest.class);
    }

    private PartyIdentifierCreateRequest readUnchecked(String body) {
        try { return read(body); }
        catch (JsonProcessingException failure) { throw new AssertionError("Expected valid Month-based calendar fixture", failure); }
    }

    private static String with(String fields) { return VALID.substring(0, VALID.length() - 1) + ',' + fields + '}'; }
}
