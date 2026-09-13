package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonPatch;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
                "zzz",
                validIdentifier());
        NaturalPersonPutRequest invalidPut = new NaturalPersonPutRequest(
                null,
                " ",
                null,
                null,
                null,
                "USA");

        assertEquals(5, validator.validate(invalidCreate.normalizedForValidation()).size());
        assertEquals(3, validator.validate(invalidPut.normalizedForValidation()).size());
    }

    @Test
    void preparesCanonicalRecordCopiesWithoutChangingRawInput() {
        LocalDate birthDate = LocalDate.of(1815, Month.DECEMBER, 10);
        LocalDate dateOfDeath = LocalDate.of(1852, Month.NOVEMBER, 27);
        InitialPartyIdentifierCreateRequest identifier = new InitialPartyIdentifierCreateRequest(
                " scheme ", " ab-123 ", " issuer ", birthDate, dateOfDeath, true);
        NaturalPersonCreateRequest raw = new NaturalPersonCreateRequest(
                "  Ada L.  ", "\u2003Ada\u2003", "  LoveLace\t", " aDa ", birthDate, dateOfDeath,
                "\n gB \t", identifier);
        NaturalPersonCreateRequest copy = raw.normalizedForValidation();

        assertNotSame(raw, copy);
        assertEquals(new NaturalPersonCreateRequest(
                "ADA L.", "ADA", "LOVELACE", "ADA", birthDate, dateOfDeath, "GB", identifier), copy);
        assertEquals(new NaturalPersonCreateRequest(
                "  Ada L.  ", "\u2003Ada\u2003", "  LoveLace\t", " aDa ", birthDate, dateOfDeath,
                "\n gB \t", identifier), raw);
        assertSame(identifier, copy.initialIdentifier());
        assertTrue(validator.validate(copy).isEmpty());
        assertEquals(copy, copy.normalizedForValidation());

        NaturalPersonPutRequest rawPut = new NaturalPersonPutRequest(
                raw.givenNames(), raw.familyNames(), raw.preferredName(), birthDate, dateOfDeath,
                raw.birthCountryCode());
        NaturalPersonPutRequest putCopy = rawPut.normalizedForValidation();
        assertNotSame(rawPut, putCopy);
        assertEquals(new NaturalPersonPutRequest("ADA", "LOVELACE", "ADA", birthDate, dateOfDeath, "GB"), putCopy);
        assertEquals(new NaturalPersonPutRequest(
                "\u2003Ada\u2003", "  LoveLace\t", " aDa ", birthDate, dateOfDeath, "\n gB \t"), rawPut);
        assertTrue(validator.validate(putCopy).isEmpty());
    }

    @Test
    void preservesNullsAndValidatesBlanksAfterNormalization() {
        NaturalPersonCreateRequest nullable = new NaturalPersonCreateRequest(
                null, "Ada", "Lovelace", null, null, null, null, validIdentifier()).normalizedForValidation();
        assertNull(nullable.displayName());
        assertNull(nullable.preferredName());
        assertNull(nullable.birthCountryCode());
        assertTrue(validator.validate(nullable).isEmpty());

        NaturalPersonCreateRequest blank = new NaturalPersonCreateRequest(
                "\u2003 ", "\u2003 ", null, "\u2003 ", null, null, "\u2003 ", validIdentifier())
                .normalizedForValidation();
        assertEquals("", blank.displayName());
        assertEquals("", blank.preferredName());
        assertNull(blank.familyNames());
        assertEquals(Set.of("given-names-required", "family-names-required", "birth-country-code-invalid"),
                validationCodes(blank));
        assertEquals(Set.of("given-names-required", "family-names-required", "birth-country-code-invalid"),
                validationCodes(new NaturalPersonPutRequest(null, "\u2003 ", "\u2003 ", null, null, " ")
                        .normalizedForValidation()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "\u00df", " \u00df ", "\u0131s", "\uff47\uff42", "USA", "U S", "\u00a0gb\u00a0" })
    void rejectsInvalidCountriesWithoutUppercaseExpansion(String country) {
        NaturalPersonCreateRequest create = new NaturalPersonCreateRequest(
                null, "Ada", "Lovelace", null, null, null, country, validIdentifier()).normalizedForValidation();
        NaturalPersonPutRequest put = new NaturalPersonPutRequest(
                "Ada", "Lovelace", null, null, null, country).normalizedForValidation();
        NaturalPersonPatchRequest patch = new NaturalPersonPatchRequest();
        patch.setBirthCountryCode(country);

        assertEquals(country.strip(), create.birthCountryCode());
        assertEquals(Set.of("birth-country-code-invalid"), validationCodes(create));
        assertEquals(Set.of("birth-country-code-invalid"), validationCodes(put));
        assertEquals(Set.of("birth-country-code-invalid"), validationCodes(patch.normalizedForValidation()));
        assertEquals(country, patch.toPatch().birthCountryCode().value());
    }

    @Test
    void checksNormalizedUtf16LengthsIncludingUppercaseExpansion() {
        String expandedName = "\u00df".repeat(101);
        NaturalPersonCreateRequest create = new NaturalPersonCreateRequest(
                "\u00df".repeat(151), expandedName, expandedName, expandedName, null, null, null, validIdentifier());
        assertTrue(validator.validate(create).isEmpty());
        assertEquals(Set.of("display-name-too-long", "given-names-too-long", "family-names-too-long",
                "preferred-name-too-long"), validationCodes(create.normalizedForValidation()));

        NaturalPersonPutRequest put = new NaturalPersonPutRequest(
                expandedName, expandedName, expandedName, null, null, null);
        assertEquals(Set.of("given-names-too-long", "family-names-too-long", "preferred-name-too-long"),
                validationCodes(put.normalizedForValidation()));
        NaturalPersonPatchRequest patch = new NaturalPersonPatchRequest();
        patch.setGivenNames(expandedName);
        patch.setFamilyNames(expandedName);
        patch.setPreferredName(expandedName);
        assertEquals(Set.of("given-names-too-long", "family-names-too-long", "preferred-name-too-long"),
                validationCodes(patch.normalizedForValidation()));

        NaturalPersonPutRequest supplementary = new NaturalPersonPutRequest(
                "Ada", "Lovelace", "\ud83d\ude00".repeat(101), null, null, null);
        assertEquals(Set.of("preferred-name-too-long"), validationCodes(supplementary.normalizedForValidation()));
    }

    @Test
    void permitsRawTextBeyondTheLimitWhenTheNormalizedTextFits() {
        String paddedName = "\u2003" + "a".repeat(200) + "\t";
        NaturalPersonCreateRequest create = new NaturalPersonCreateRequest(
                " " + "d".repeat(300) + " ", paddedName, paddedName, paddedName, null, null, " gb ", validIdentifier());
        assertTrue(validator.validate(create.normalizedForValidation()).isEmpty());
        assertTrue(validator.validate(new NaturalPersonPutRequest(
                paddedName, paddedName, paddedName, null, null, " gb ").normalizedForValidation()).isEmpty());

        NaturalPersonPatchRequest patch = new NaturalPersonPatchRequest();
        patch.setGivenNames(paddedName);
        patch.setFamilyNames(paddedName);
        patch.setPreferredName(paddedName);
        patch.setBirthCountryCode(" gb ");
        assertTrue(validator.validate(patch.normalizedForValidation()).isEmpty());
        assertEquals(paddedName, patch.toPatch().preferredName().value());
    }

    private static InitialPartyIdentifierCreateRequest validIdentifier() {
        return new InitialPartyIdentifierCreateRequest(
                "NATIONAL_ID",
                "AB123456",
                null,
                null,
                null,
                true);
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

        assertFalse(validator.validate(empty.normalizedForValidation()).isEmpty());
        assertTrue(empty.normalizedForValidation().toPatch().isEmpty());

        NaturalPersonPatch clearPatch = clear.normalizedForValidation().toPatch();
        assertTrue(clearPatch.preferredName().isPresent());
        assertNull(clearPatch.preferredName().value());
        assertFalse(clearPatch.givenNames().isPresent());
        assertPatchEquals(clear.toPatch(), clearPatch);
        assertTrue(validator.validate(clear.normalizedForValidation()).isEmpty());

        NaturalPersonPatch suppliedPatch = supplied.normalizedForValidation().toPatch();
        assertTrue(suppliedPatch.givenNames().isPresent());
        assertEquals("ADA", suppliedPatch.givenNames().value());
        assertEquals("Ada", supplied.toPatch().givenNames().value());
        assertEquals(LocalDate.parse("1815-12-10"), suppliedPatch.birthDate().value());
        assertTrue(validator.validate(supplied.normalizedForValidation()).isEmpty());
    }

    @Test
    void copiesEveryPatchPresenceFlagWithoutMutatingValues() throws Exception {
        NaturalPersonPatchRequest raw = objectMapper.readValue("""
                {"givenNames":" Ada ","familyNames":" LoveLace ","preferredName":"  ",
                 "birthDate":"1815-12-10","dateOfDeath":null,"birthCountryCode":" gB "}
                """, NaturalPersonPatchRequest.class);
        NaturalPersonPatch before = raw.toPatch();
        NaturalPersonPatchRequest copy = raw.normalizedForValidation();
        NaturalPersonPatch normalized = copy.toPatch();

        assertNotSame(raw, copy);
        assertEquals("ADA", normalized.givenNames().value());
        assertEquals("LOVELACE", normalized.familyNames().value());
        assertEquals("", normalized.preferredName().value());
        assertEquals("GB", normalized.birthCountryCode().value());
        assertEquals(before.birthDate().isPresent(), normalized.birthDate().isPresent());
        assertEquals(before.birthDate().value(), normalized.birthDate().value());
        assertEquals(before.dateOfDeath().isPresent(), normalized.dateOfDeath().isPresent());
        assertEquals(before.dateOfDeath().value(), normalized.dateOfDeath().value());
        assertTrue(normalized.dateOfDeath().isPresent());
        assertNull(normalized.dateOfDeath().value());
        assertPatchEquals(before, raw.toPatch());
        assertTrue(validator.validate(copy).isEmpty());
        copy.setGivenNames("CHANGED");
        assertPatchEquals(before, raw.toPatch());

        NaturalPersonPatchRequest clear = objectMapper.readValue("""
                {"preferredName":null,"birthDate":null,"dateOfDeath":null,"birthCountryCode":null}
                """, NaturalPersonPatchRequest.class);
        assertPatchEquals(clear.toPatch(), clear.normalizedForValidation().toPatch());
        assertTrue(validator.validate(clear.normalizedForValidation()).isEmpty());
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

        assertEquals(Set.of("given-names-required"), validationCodes(nullRequired.normalizedForValidation()));
        assertEquals(Set.of("family-names-required"), validationCodes(blankRequired.normalizedForValidation()));
        assertEquals(Set.of("birth-country-code-invalid"), validationCodes(invalidCountry.normalizedForValidation()));
        assertEquals(Set.of("preferred-name-too-long"), validationCodes(oversized.normalizedForValidation()));
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

    private static void assertPatchEquals(NaturalPersonPatch expected, NaturalPersonPatch actual) {
        List<FieldUpdate<?>> expectedFields = List.of(expected.givenNames(), expected.familyNames(),
                expected.preferredName(), expected.birthDate(), expected.dateOfDeath(), expected.birthCountryCode());
        List<FieldUpdate<?>> actualFields = List.of(actual.givenNames(), actual.familyNames(),
                actual.preferredName(), actual.birthDate(), actual.dateOfDeath(), actual.birthCountryCode());
        for (int index = 0; index < expectedFields.size(); index++) {
            assertEquals(expectedFields.get(index).isPresent(), actualFields.get(index).isPresent());
            assertEquals(expectedFields.get(index).value(), actualFields.get(index).value());
        }
    }

    private Set<String> validationCodes(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessageTemplate)
                .collect(Collectors.toSet());
    }
}
