package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.support.IdentifierSchemeRequestParameters;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the closed PATCH transport contract and exact presence-aware Application input. */
class IdentifierSchemePatchRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final IdentifierSchemeRequestParameters parameters = new IdentifierSchemeRequestParameters();

    @Test
    void mapsOnlySuppliedFieldsAndDistinguishesClearFromOmission() throws Exception {
        var changes = parameters.patch(read("{\"name\":\" Exact Name \",\"description\":null}"));
        assertTrue(changes.name().isPresent());
        assertEquals(" Exact Name ", changes.name().value());
        assertTrue(changes.description().isPresent());
        assertNull(changes.description().value());
        assertFalse(changes.normalizerKey().isPresent());
        assertFalse(changes.validatorKey().isPresent());
        assertFalse(changes.minimumLength().isPresent());
        assertFalse(changes.maximumLength().isPresent());
        assertFalse(changes.requiresExpiration().isPresent());
        var cleared = parameters.patch(read("{\"minimumLength\":null,\"maximumLength\":null,\"requiresExpiration\":false}"));
        assertTrue(cleared.minimumLength().isPresent());
        assertNull(cleared.minimumLength().value());
        assertTrue(cleared.maximumLength().isPresent());
        assertNull(cleared.maximumLength().value());
        assertEquals(false, cleared.requiresExpiration().value());
    }

    @Test
    void preservesHugeSignedIncoherentBoundsAndHistoricalKeysForBusinessPrecedence() throws Exception {
        String huge = "9".repeat(300);
        var changes = parameters.patch(read("{\"minimumLength\":" + huge + ",\"maximumLength\":-1,"
                + "\"normalizerKey\":\" historical \",\"validatorKey\":\"unknown\",\"requiresExpiration\":true}"));
        assertEquals(new BigInteger(huge), changes.minimumLength().value());
        assertEquals(BigInteger.valueOf(-1), changes.maximumLength().value());
        assertEquals(" historical ", changes.normalizerKey().value());
        assertEquals("unknown", changes.validatorKey().value());
        assertEquals(true, changes.requiresExpiration().value());
    }

    @Test
    void rejectsMissingNullEmptyAndInvalidTextWithStableCodes() throws Exception {
        assertCode(PartyResponseCode.REQUEST_BODY_REQUIRED, null);
        assertCode(PartyResponseCode.REQUEST_BODY_REQUIRED, read("null"));
        assertCode(PartyResponseCode.PATCH_PROPERTY_REQUIRED, read("{}"));
        for (String field : new String[]{"name", "normalizerKey", "validatorKey", "requiresExpiration"}) {
            assertCode(PartyResponseCode.BAD_REQUEST, read("{\"" + field + "\":null}"));
        }
        for (String field : new String[]{"name", "normalizerKey", "validatorKey"}) {
            int maximum = field.equals("name") ? 150 : 64;
            for (String value : new String[]{"", "  ", "😀".repeat(maximum + 1)}) {
                assertCode(PartyResponseCode.BAD_REQUEST, read("{\"" + field + "\":\"" + value + "\"}"));
            }
            parameters.patch(read("{\"" + field + "\":\"" + "😀".repeat(maximum) + "\"}"));
        }
        parameters.patch(read("{\"description\":\"\"}"));
        parameters.patch(read("{\"description\":\"" + "😀".repeat(500) + "\"}"));
        assertCode(PartyResponseCode.BAD_REQUEST, read("{\"description\":\"" + "😀".repeat(501) + "\"}"));
    }

    @Test
    void rejectsEveryImmutableUnknownDuplicateAndCoercedProperty() {
        for (String field : new String[]{"id", "code", "issuingCountryCode", "category", "applicableSubjectType",
                "status", "version", "createdAt", "updatedAt", "createdBy", "updatedBy", "extra"}) {
            assertThrows(JsonProcessingException.class, () -> read("{\"name\":\"valid\",\"" + field + "\":null}"));
        }
        for (String body : new String[]{"[]", "true", "3", "\"text\"", "{", "{} {}",
                "{\"name\":\"a\",\"name\":\"b\"}", "{\"description\":null,\"description\":null}",
                "{\"minimumLength\":\"1\"}", "{\"minimumLength\":1.0}", "{\"maximumLength\":1e2}",
                "{\"maximumLength\":false}", "{\"name\":42}", "{\"description\":[]}",
                "{\"normalizerKey\":{}}", "{\"validatorKey\":true}", "{\"requiresExpiration\":\"false\"}"}) {
            assertThrows(JsonProcessingException.class, () -> read(body), body);
        }
    }

    private IdentifierSchemePatchRequest read(String body) throws Exception {
        return mapper.readValue(body, IdentifierSchemePatchRequest.class);
    }

    private void assertCode(PartyResponseCode code, IdentifierSchemePatchRequest request) {
        assertEquals(code, assertThrows(ApiResponseException.class, () -> parameters.patch(request)).getResponseCode());
    }
}
