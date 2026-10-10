package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.support.PartyIdentifierActionBodies;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pins exact optional reasons, code-point limits and byte-presence verification without transport stream access. */
class PartyIdentifierReasonBindingTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void absenceEmptyObjectAndOmittedPropertiesProduceOneNoReasonDefault() throws Exception {
        var absent = PartyIdentifierActionBodies.reasons(null);
        assertNull(absent.reasonCode()); assertNull(absent.reasonDetail());
        assertEquals(absent, PartyIdentifierActionBodies.reasons(read("{}")));
        var empty = PartyIdentifierActionBodies.reasons(read("{\"reasonCode\":\"\",\"reasonDetail\":\"\"}"));
        assertEquals("", empty.reasonCode()); assertEquals("", empty.reasonDetail());
        assertEquals(absent, PartyIdentifierActionBodies.reasons(new byte[0], json));
        byte[] whitespace = " \r\n\t".getBytes(StandardCharsets.UTF_8);
        assertEquals("bad-request", assertThrows(ApiResponseException.class, () -> PartyIdentifierActionBodies.reasons(whitespace, json)).getResponseCode().getCode());
    }

    @Test
    void exactStringsControlsAndSupplementaryBoundariesRemainUntouched() throws Exception {
        String code = "😀".repeat(64);
        String detail = "😀".repeat(1000);
        var request = read(json.writeValueAsString(Map.of("reasonCode", code, "reasonDetail", detail)));
        var valid = PartyIdentifierActionBodies.reasons(request);
        assertEquals(code, valid.reasonCode()); assertEquals(detail, valid.reasonDetail());
        String controls = " \u0000\n\t\u0085 ";
        var exact = PartyIdentifierActionBodies.reasons(read(json.writeValueAsString(Map.of("reasonDetail", controls))));
        assertEquals(controls, exact.reasonDetail()); assertNull(exact.reasonCode()); assertFalse(exact.toString().contains(controls));
    }

    @Test
    void literalNullNullPropertiesUnknownDuplicatesCoercionAndTrailingTokensAreRejected() {
        for (String body : List.of("null", "null {}", "[]", "42", "{", "{\"reasonCode\":null}", "{\"reasonDetail\":null}",
                "{\"reasonCode\":false}", "{\"reasonDetail\":42}", "{\"extra\":null}",
                "{\"reasonCode\":\"\",\"reasonCode\":\"\"}", "{\"reasonCode\":\"\",\"reason\\u0043ode\":\"\"}", "{} {}")) {
            assertThrows(JsonProcessingException.class, () -> read(body));
        }
    }

    @Test
    void exceededCodePointLimitsUseOnlyGenericBadRequest() {
        var code = new PartyIdentifierReasonRequest("😀".repeat(65), null);
        var detail = new PartyIdentifierReasonRequest(null, "😀".repeat(1001));
        assertEquals("bad-request", assertThrows(ApiResponseException.class, () -> PartyIdentifierActionBodies.reasons(code)).getResponseCode().getCode());
        assertEquals("bad-request", assertThrows(ApiResponseException.class, () -> PartyIdentifierActionBodies.reasons(detail)).getResponseCode().getCode());
    }

    @Test
    void verifyRejectsEveryNonzeroBufferedBodyAndAcceptsOnlyActualAbsence() {
        PartyIdentifierActionBodies.verify(null); PartyIdentifierActionBodies.verify(new byte[0]);
        for (byte[] body : List.of("{}".getBytes(StandardCharsets.UTF_8), "null".getBytes(StandardCharsets.UTF_8),
                " \r\n\t".getBytes(StandardCharsets.UTF_8), new byte[]{0}, new byte[]{(byte) 0xff})) {
            assertEquals("bad-request", assertThrows(ApiResponseException.class, () -> PartyIdentifierActionBodies.verify(body)).getResponseCode().getCode());
        }
    }

    private PartyIdentifierReasonRequest read(String body) throws JsonProcessingException { return json.readValue(body, PartyIdentifierReasonRequest.class); }
}
