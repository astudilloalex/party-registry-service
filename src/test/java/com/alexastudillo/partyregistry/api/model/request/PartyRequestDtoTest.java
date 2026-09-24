package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.Month;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies strict Party registration DTO validation and identifier
 * confidentiality.
 */
class PartyRequestDtoTest {

    private static final String COMPLETE_IDENTIFIER = "AB123456";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requiresAndCascadesTheInitialIdentifierForBothPartyTypes() throws ReflectiveOperationException {
        assertNestedConstraint(NaturalPersonCreateRequest.class);
        assertNestedConstraint(LegalEntityCreateRequest.class);

        NaturalPersonCreateRequest missing = new NaturalPersonCreateRequest(
                null, "Ada", "Lovelace", null, null, null, null, null);
        LegalEntityCreateRequest invalidNested = new LegalEntityCreateRequest(
                null,
                "Analytical Engines Ltd",
                null,
                null,
                "EC",
                null,
                null,
                new InitialPartyIdentifierCreateRequest(" ", " ", null, null, null, false));

        assertFalse(validator.validate(missing.normalizedForValidation()).isEmpty());
        assertEquals(2, validator.validate(invalidNested.normalizedForValidation()).size());
    }

    @Test
    void preparesCanonicalLegalEntityCopyWithoutChangingRawInput() {
        LocalDate incorporatedOn = LocalDate.of(2000, Month.JANUARY, 2);
        LocalDate dissolvedOn = LocalDate.of(2020, Month.JANUARY, 2);
        InitialPartyIdentifierCreateRequest identifier = new InitialPartyIdentifierCreateRequest(
                " scheme ", " ab-123 ", " issuer ", incorporatedOn, dissolvedOn, true);
        LegalEntityCreateRequest raw = new LegalEntityCreateRequest(
                " Company ", "\u2003Stra\u00dfe Ltd\u2003", " Trade ", " ltd ", "\t eC \n",
                incorporatedOn, dissolvedOn, identifier);
        LegalEntityCreateRequest copy = raw.normalizedForValidation();

        assertNotSame(raw, copy);
        assertEquals(new LegalEntityCreateRequest("COMPANY", "STRASSE LTD", "TRADE", "LTD", "EC",
                incorporatedOn, dissolvedOn, identifier), copy);
        assertEquals(new LegalEntityCreateRequest(
                " Company ", "\u2003Stra\u00dfe Ltd\u2003", " Trade ", " ltd ", "\t eC \n",
                incorporatedOn, dissolvedOn, identifier), raw);
        assertSame(identifier, copy.initialIdentifier());
        assertTrue(validator.validate(copy).isEmpty());
        assertEquals(copy, copy.normalizedForValidation());
    }

    @Test
    void preservesLegalNullsAndOptionalBlanksForExistingValidationRules() {
        LegalEntityCreateRequest nullable = new LegalEntityCreateRequest(
                null, "Company", null, null, "EC", null, null, null).normalizedForValidation();
        assertNull(nullable.displayName());
        assertNull(nullable.tradeName());
        assertNull(nullable.legalFormCode());
        assertNull(nullable.initialIdentifier());
        assertEquals(Set.of("initial-identifier-required"), validationCodes(nullable));

        InitialPartyIdentifierCreateRequest identifier = new InitialPartyIdentifierCreateRequest(
                "SCHEME", COMPLETE_IDENTIFIER, null, null, null, true);
        LegalEntityCreateRequest blank = new LegalEntityCreateRequest(
                "\u2003 ", "\u2003 ", "\u2003 ", "\u2003 ", "\u2003 ", null, null, identifier)
                .normalizedForValidation();
        assertEquals("", blank.displayName());
        assertEquals("", blank.tradeName());
        assertEquals("", blank.legalFormCode());
        assertEquals(Set.of("legal-name-required", "legal-name-invalid", "incorporation-country-code-invalid"),
                validationCodes(blank));
        assertEquals(Set.of("legal-name-required", "incorporation-country-code-required"),
                validationCodes(new LegalEntityCreateRequest(null, null, null, null, null, null, null,
                        identifier)
                        .normalizedForValidation()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "\u00df", " \u00df ", "\u0131s", "\uff45\uff43", "USA", "E C", "\u00a0ec\u00a0" })
    void rejectsInvalidLegalCountriesWithoutUppercaseExpansion(String country) {
        LegalEntityCreateRequest raw = new LegalEntityCreateRequest(
                null, "Company", null, null, country, null, null,
                new InitialPartyIdentifierCreateRequest("SCHEME", COMPLETE_IDENTIFIER, null, null, null,
                        true));
        LegalEntityCreateRequest copy = raw.normalizedForValidation();

        assertEquals(country.strip(), copy.incorporationCountryCode());
        assertEquals(Set.of("incorporation-country-code-invalid"), validationCodes(copy));
        assertEquals(country, raw.incorporationCountryCode());
    }

    @Test
    void checksExpandedLegalTextLengthsAfterTrimming() {
        InitialPartyIdentifierCreateRequest identifier = new InitialPartyIdentifierCreateRequest(
                "SCHEME", COMPLETE_IDENTIFIER, null, null, null, true);
        String expandedName = "\u00df".repeat(151);
        LegalEntityCreateRequest expanded = new LegalEntityCreateRequest(
                expandedName, expandedName, expandedName, "\u00df".repeat(33), "EC", null, null,
                identifier);
        assertTrue(validator.validate(expanded).isEmpty());
        assertEquals(Set.of("display-name-too-long", "legal-name-invalid", "trade-name-too-long",
                "legal-form-code-too-long"), validationCodes(expanded.normalizedForValidation()));

        String paddedName = "\u2003" + "a".repeat(300) + "\t";
        LegalEntityCreateRequest padded = new LegalEntityCreateRequest(
                paddedName, paddedName, paddedName, " " + "a".repeat(64) + " ", " ec ", null, null,
                identifier);
        assertTrue(validator.validate(padded.normalizedForValidation()).isEmpty());
        assertEquals(paddedName, padded.legalName());
    }

    @Test
    void leavesRawIdentifierLimitsUnchangedInValidationCopies() {
        InitialPartyIdentifierCreateRequest identifier = new InitialPartyIdentifierCreateRequest(
                " " + "s".repeat(64) + " ", " " + "v".repeat(256) + " ", " " + "i".repeat(64) + " ",
                null, null, true);
        Set<String> expected = Set.of("identifier-scheme-code-too-long", "identifier-value-too-long",
                "issuer-code-too-long");
        assertEquals(expected, validationCodes(new NaturalPersonCreateRequest(
                null, "Ada", "Lovelace", null, null, null, null, identifier)
                .normalizedForValidation()));
        assertEquals(expected, validationCodes(new LegalEntityCreateRequest(
                null, "Company", null, null, "EC", null, null, identifier).normalizedForValidation()));
    }

    @Test
    void appliesOmittedIdentifierOptionalsAndPrimaryDefault() throws Exception {
        PartyIdentifierCreateRequest request = objectMapper.readValue("""
                {
                  "identifierSchemeCode": "TEST_NATURAL_ACTIVE",
                  "value": "AB123456"
                }
                """, PartyIdentifierCreateRequest.class);

        assertFalse(request.isPrimary());
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void marksCompleteValuesWriteOnlyAndRedactsGeneratedDiagnostics() throws Exception {
        assertWriteOnly(InitialPartyIdentifierCreateRequest.class);
        assertWriteOnly(PartyIdentifierCreateRequest.class);
        InitialPartyIdentifierCreateRequest initial = new InitialPartyIdentifierCreateRequest(
                "TEST_NATURAL_ACTIVE", COMPLETE_IDENTIFIER, "AUTHORITY", null, null, true);
        PartyIdentifierCreateRequest additional = new PartyIdentifierCreateRequest(
                "TEST_NATURAL_ACTIVE", COMPLETE_IDENTIFIER, "AUTHORITY", null, null, false);

        for (Object request : new Object[] { initial, additional }) {
            String json = objectMapper.writeValueAsString(request);
            assertFalse(json.contains(COMPLETE_IDENTIFIER));
            assertFalse(json.contains("\"value\""));
            assertFalse(request.toString().contains(COMPLETE_IDENTIFIER));
            assertTrue(request.toString().contains("<redacted>"));
        }
    }

    @Test
    void rejectsUnknownPropertiesOnNewRequestTypes() {
        assertThrows(UnrecognizedPropertyException.class, () -> objectMapper.readValue("""
                {
                  "identifierSchemeCode": "TEST_NATURAL_ACTIVE",
                  "value": "AB123456",
                  "unsupported": true
                }
                """, PartyIdentifierCreateRequest.class));
    }

    private static void assertNestedConstraint(Class<?> requestType) throws ReflectiveOperationException {
        Field field = requestType.getDeclaredField("initialIdentifier");
        assertNotNull(field.getAnnotation(NotNull.class));
        assertNotNull(field.getAnnotation(Valid.class));
    }

    private static void assertWriteOnly(Class<?> requestType) throws ReflectiveOperationException {
        JsonProperty property = requestType.getDeclaredField("value").getAnnotation(JsonProperty.class);
        assertNotNull(property);
        assertEquals(JsonProperty.Access.WRITE_ONLY, property.access());
    }

    private Set<String> validationCodes(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessageTemplate)
                .collect(Collectors.toSet());
    }
}
