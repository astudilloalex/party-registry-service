package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.support.NationalityRequestParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies strict create/PATCH shape, ASCII normalization, and presence-aware dates. */
class NationalityRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final NationalityRequestParameters parameters = new NationalityRequestParameters();

    @Test
    void createUsesEffectiveDefaultsAndExactCountrySyntax() throws Exception {
        NationalityCreateRequest request = mapper.readValue("{\"countryCode\":\"  ec  \"}",
                NationalityCreateRequest.class);
        assertEquals("EC", parameters.countryCode(request));
        assertFalse(request.isPrimary());
        assertNull(request.validFrom());
        assertNull(request.validUntil());
        assertEquals(PartyResponseCode.REQUEST_BODY_REQUIRED,
                assertThrows(ApiResponseException.class, () -> parameters.countryCode(null)).getResponseCode());
        NationalityCreateRequest emptyRequest = mapper.readValue("{}", NationalityCreateRequest.class);
        assertEquals(PartyResponseCode.COUNTRY_CODE_REQUIRED,
                assertThrows(ApiResponseException.class, () -> parameters.countryCode(emptyRequest))
                        .getResponseCode());
        for (String value : new String[] {"éC", "E C", "E1", "ＥＣ"}) {
            NationalityCreateRequest invalidRequest = new NationalityCreateRequest(value, false, null, null);
            assertEquals(PartyResponseCode.COUNTRY_CODE_INVALID,
                    assertThrows(ApiResponseException.class, () -> parameters.countryCode(invalidRequest))
                            .getResponseCode());
        }
    }

    @Test
    void rejectsUnknownDuplicateWrongTypeAndInvalidDateForCreate() {
        for (String body : new String[] {
                "[]", "7", "{\"countryCode\":7}", "{\"countryCode\":true}",
                "{\"countryCode\":\"EC\",\"extra\":1}",
                "{\"countryCode\":\"EC\",\"countryCode\":\"CO\"}",
                "{\"countryCode\":\"EC\",\"isPrimary\":null}",
                "{\"countryCode\":\"EC\",\"isPrimary\":\"true\"}",
                "{\"countryCode\":\"EC\",\"validFrom\":\"2026-02-30\"}",
                "{\"countryCode\":\"EC\",\"validUntil\":123}"}) {
            assertThrows(Exception.class, () -> mapper.readValue(body, NationalityCreateRequest.class), body);
        }
    }

    @Test
    void patchDistinguishesOmissionExplicitNullAndNonemptyRequirement() throws Exception {
        NationalityPatchRequest patch = mapper.readValue("{\"validUntil\":null}", NationalityPatchRequest.class);
        assertFalse(patch.validFrom().isPresent());
        assertTrue(patch.validUntil().isPresent());
        assertNull(patch.validUntil().value());
        NationalityPatchRequest dated = mapper.readValue("{\"validFrom\":\"2026-09-23\"}",
                NationalityPatchRequest.class);
        assertEquals(LocalDate.of(2026, Month.SEPTEMBER, 23), dated.validFrom().value());
        NationalityPatchRequest emptyPatch = mapper.readValue("{}", NationalityPatchRequest.class);
        assertEquals(PartyResponseCode.PATCH_PROPERTY_REQUIRED,
                assertThrows(ApiResponseException.class, () -> parameters.patch(emptyPatch))
                        .getResponseCode());
        assertEquals(PartyResponseCode.REQUEST_BODY_REQUIRED,
                assertThrows(ApiResponseException.class, () -> parameters.patch(null)).getResponseCode());
    }

    @Test
    void patchRejectsUnknownDuplicateAndWrongDateTokens() {
        for (String body : new String[] {"[]", "{\"isPrimary\":false}",
                "{\"validFrom\":null,\"validFrom\":null}",
                "{\"validUntil\":true}", "{\"validFrom\":\"2026-02-30\"}"}) {
            assertThrows(Exception.class, () -> mapper.readValue(body, NationalityPatchRequest.class), body);
        }
    }
}
