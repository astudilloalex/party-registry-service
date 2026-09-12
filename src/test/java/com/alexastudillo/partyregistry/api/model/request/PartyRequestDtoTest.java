package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies strict Party registration DTO validation and identifier confidentiality.
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

        assertFalse(validator.validate(missing).isEmpty());
        assertEquals(2, validator.validate(invalidNested).size());
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

        for (Object request : new Object[]{initial, additional}) {
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
}
