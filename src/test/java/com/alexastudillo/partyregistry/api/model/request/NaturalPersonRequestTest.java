package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.model.NaturalPersonPatch;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies strict natural-person request deserialization and validation.
 */
class NaturalPersonRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validatesCreateAndReplacementFields() {
        NaturalPersonCreateRequest invalidCreate = new NaturalPersonCreateRequest(
                "d".repeat(301),
                " ",
                "f".repeat(201),
                "p".repeat(201),
                null,
                null,
                "zz");
        NaturalPersonPutRequest invalidPut = new NaturalPersonPutRequest(
                null,
                " ",
                null,
                null,
                null,
                "USA");

        assertEquals(5, validator.validate(invalidCreate).size());
        assertEquals(3, validator.validate(invalidPut).size());
    }

    @Test
    void distinguishesAbsentExplicitNullAndSuppliedPatchValues() throws Exception {
        NaturalPersonPatchRequest empty = objectMapper.readValue("{}", NaturalPersonPatchRequest.class);
        NaturalPersonPatchRequest clear = objectMapper.readValue(
                "{\"preferredName\":null}",
                NaturalPersonPatchRequest.class);
        NaturalPersonPatchRequest supplied = objectMapper.readValue(
                "{\"givenNames\":\"Ada\",\"birthDate\":\"1815-12-10\"}",
                NaturalPersonPatchRequest.class);

        assertFalse(validator.validate(empty).isEmpty());

        NaturalPersonPatch clearPatch = clear.toPatch();
        assertTrue(clearPatch.preferredName().isPresent());
        assertNull(clearPatch.preferredName().value());
        assertFalse(clearPatch.givenNames().isPresent());
        assertTrue(validator.validate(clear).isEmpty());

        NaturalPersonPatch suppliedPatch = supplied.toPatch();
        assertTrue(suppliedPatch.givenNames().isPresent());
        assertEquals("Ada", suppliedPatch.givenNames().value());
        assertEquals(LocalDate.parse("1815-12-10"), suppliedPatch.birthDate().value());
        assertTrue(validator.validate(supplied).isEmpty());
    }

    @Test
    void rejectsNullBlankAndInvalidPatchValues() throws Exception {
        NaturalPersonPatchRequest nullRequired = objectMapper.readValue(
                "{\"givenNames\":null}",
                NaturalPersonPatchRequest.class);
        NaturalPersonPatchRequest blankRequired = objectMapper.readValue(
                "{\"familyNames\":\"  \"}",
                NaturalPersonPatchRequest.class);
        NaturalPersonPatchRequest invalidCountry = objectMapper.readValue(
                "{\"birthCountryCode\":\"usa\"}",
                NaturalPersonPatchRequest.class);
        NaturalPersonPatchRequest oversized = objectMapper.readValue(
                "{\"preferredName\":\"" + "p".repeat(201) + "\"}",
                NaturalPersonPatchRequest.class);

        assertFalse(validator.validate(nullRequired).isEmpty());
        assertFalse(validator.validate(blankRequired).isEmpty());
        assertFalse(validator.validate(invalidCountry).isEmpty());
        assertFalse(validator.validate(oversized).isEmpty());
    }

    @Test
    void rejectsUnsupportedPropertiesAndMalformedDates() {
        assertThrows(
                UnrecognizedPropertyException.class,
                () -> objectMapper.readValue(
                        "{\"unsupported\":true}",
                        NaturalPersonPatchRequest.class));
        assertThrows(
                InvalidFormatException.class,
                () -> objectMapper.readValue(
                        "{\"birthDate\":\"not-a-date\"}",
                        NaturalPersonPatchRequest.class));
    }
}
