package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.LegalEntityUpdateJsonReaderInterceptor;
import com.alexastudillo.partyregistry.api.support.ApiRequestSupport;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies legal-update JSON typing, presence, normalization and deterministic
 * public validation codes.
 */
class LegalEntityRequestValidationTest {

    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private final ApiRequestSupport support = new ApiRequestSupport(VALIDATORS.getValidator());
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @AfterAll
    static void closeValidationFactory() {
        VALIDATORS.close();
    }

    @Test
    void putValidatesTheNormalizedCopyButReturnsOriginalInputs() throws Exception {
        LegalEntityPutRequest request = json.readValue("""
                {"legalName":"  Compañía Águila  ","tradeName":" brand ","legalFormCode":" sa ",
                 "incorporationCountryCode":" ec ","incorporatedOn":"2020-02-29","dissolvedOn":null}
                """, LegalEntityPutRequest.class);
        assertSame(request, support.validateBody(request, LegalEntityPutRequest::normalizedForValidation));
        assertEquals("  Compañía Águila  ", request.legalName());
        LegalEntityPutRequest normalized = request.normalizedForValidation();
        assertEquals("COMPAÑÍA ÁGUILA", normalized.legalName());
        assertEquals("BRAND", normalized.tradeName());
        assertEquals("SA", normalized.legalFormCode());
        assertEquals("EC", normalized.incorporationCountryCode());
        assertEquals(LocalDate.of(2020, Month.FEBRUARY, 29), normalized.incorporatedOn());
        assertNull(normalized.dissolvedOn());
    }

    @Test
    void putRequiredCodesUseJsonFieldOrdering() throws Exception {
        assertCode("incorporation-country-code-required", () -> validatePut(new LegalEntityPutRequest(
                null, null, null, null, null, null)));
        assertCode("legal-name-required", () -> validatePut(new LegalEntityPutRequest(
                " ", null, null, "EC", null, null)));
        assertCode("request-body-required", () -> validatePut(null));
        assertCode("request-body-required", () -> validatePatch(null));
        assertNull(json.readValue("null", LegalEntityPutRequest.class));
        assertNull(json.readValue("null", LegalEntityPatchRequest.class));
    }

    @Test
    void patchKeepsEveryPresenceStateThroughValidationCopies() throws Exception {
        List<String> fields = List.of("legalName", "tradeName", "legalFormCode", "incorporationCountryCode",
                "incorporatedOn", "dissolvedOn");
        for (String field : fields) {
            boolean date = field.endsWith("On");
            String value = date ? "2020-01-15" : field.equals("incorporationCountryCode") ? " ec " : " Mixed ";
            LegalEntityPatchRequest request = json.readValue("{\"" + field + "\":\"" + value + "\"}",
                    LegalEntityPatchRequest.class);
            assertSame(request, support.validateBody(request, LegalEntityPatchRequest::normalizedForValidation));
            var original = request.toPatch();
            var normalized = request.normalizedForValidation().toPatch();
            var originalValues = List.of(original.legalName(), original.tradeName(), original.legalFormCode(),
                    original.incorporationCountryCode(), original.incorporatedOn(), original.dissolvedOn());
            var normalizedValues = List.of(normalized.legalName(), normalized.tradeName(), normalized.legalFormCode(),
                    normalized.incorporationCountryCode(), normalized.incorporatedOn(), normalized.dissolvedOn());
            for (int index = 0; index < fields.size(); index++) {
                assertEquals(fields.get(index).equals(field), originalValues.get(index).isPresent());
                assertEquals(originalValues.get(index).isPresent(), normalizedValues.get(index).isPresent());
                if (fields.get(index).equals(field)) {
                    assertEquals(date ? LocalDate.parse(value) : value, originalValues.get(index).value());
                    assertEquals(date ? LocalDate.parse(value) : value.strip().toUpperCase(java.util.Locale.ROOT),
                            normalizedValues.get(index).value());
                }
            }
        }
        LegalEntityPatchRequest clear = json.readValue("{\"tradeName\":null}", LegalEntityPatchRequest.class);
        validatePatch(clear);
        assertTrue(clear.normalizedForValidation().toPatch().tradeName().isPresent());
        assertNull(clear.normalizedForValidation().toPatch().tradeName().value());
        assertFalse(clear.toPatch().legalName().isPresent());
    }

    @Test
    void patchRequiredAndEmptyCodesUsePublicPropertyPaths() throws Exception {
        LegalEntityPatchRequest empty = json.readValue("{}", LegalEntityPatchRequest.class);
        assertCode("patch-property-required", () -> validatePatch(empty));
        LegalEntityPatchRequest missing = json.readValue(
                "{\"legalName\":null,\"incorporationCountryCode\":null}", LegalEntityPatchRequest.class);
        assertEquals(java.util.Set.of("legalName", "incorporationCountryCode"), VALIDATORS.getValidator()
                .validate(missing).stream().map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet()));
        assertCode("incorporation-country-code-required", () -> validatePatch(missing));
        LegalEntityPatchRequest blank = json.readValue("{\"legalName\":\"  \"}", LegalEntityPatchRequest.class);
        assertCode("legal-name-required", () -> validatePatch(blank));
    }

    @Test
    void normalizedBoundariesAndCountryRepresentationHaveSpecificCodes() {
        validatePut(new LegalEntityPutRequest("ß".repeat(150), "😀".repeat(150), "ß".repeat(32), "EC", null, null));
        assertCode("legal-name-invalid", () -> validatePut(new LegalEntityPutRequest(
                "😀".repeat(151), null, null, "EC", null, null)));
        assertCode("trade-name-too-long", () -> validatePut(new LegalEntityPutRequest(
                "Legal", "ß".repeat(151), null, "EC", null, null)));
        assertCode("legal-form-code-too-long", () -> validatePut(new LegalEntityPutRequest(
                "Legal", null, "ß".repeat(33), "EC", null, null)));
        for (String country : List.of("", "  ", "E", "ECU", "E C", "ß", "ıS", "ＥＣ")) {
            assertCode("incorporation-country-code-invalid", () -> validatePut(new LegalEntityPutRequest(
                    "Legal", null, null, country, null, null)));
        }
    }

    @Test
    void strictStringsAndDatesRejectCoercionInBothUpdateTypes() {
        for (Class<?> type : List.of(LegalEntityPutRequest.class, LegalEntityPatchRequest.class)) {
            for (String field : List.of("legalName", "tradeName", "legalFormCode", "incorporationCountryCode")) {
                for (String invalid : List.of("42", "true", "[]", "{}")) {
                    assertThrows(JsonProcessingException.class,
                            () -> json.readValue("{\"" + field + "\":" + invalid + "}", type));
                }
            }
            for (String field : List.of("incorporatedOn", "dissolvedOn")) {
                for (String invalid : List.of("42", "true", "[2020,1,1]", "{}", "\"2020-02-30\"", "\"2020-1-1\"",
                        "\"\"")) {
                    assertThrows(JsonProcessingException.class,
                            () -> json.readValue("{\"" + field + "\":" + invalid + "}", type));
                }
            }
            for (String invalid : List.of("[]", "42", "true", "{", "\"body\"")) {
                assertThrows(JsonProcessingException.class, () -> json.readValue(invalid, type));
            }
        }
    }

    @Test
    void helperStateAndRootFieldsAreNotAcceptedAsJsonProperties() {
        for (Class<?> type : List.of(LegalEntityPutRequest.class, LegalEntityPatchRequest.class)) {
            for (String field : List.of("displayName", "recordStatus", "version", "initialIdentifier", "identifiers",
                    "empty", "legalNamePresent", "incorporationCountryCodePresent", "normalizedForValidation")) {
                assertThrows(JsonProcessingException.class, () -> json.readValue(
                        "{\"legalName\":\"Legal\",\"incorporationCountryCode\":\"EC\",\"" + field + "\":true}", type));
            }
        }
    }

    @Test
    void readerTranslatesOnlyUpdateSyntaxFailuresWithoutRetainingSensitiveCauses() throws Exception {
        var reader = new LegalEntityUpdateJsonReaderInterceptor();
        JsonProcessingException cause = new JsonParseException((JsonParser) null, "sensitive-source");
        for (Class<?> type : List.of(LegalEntityPutRequest.class, LegalEntityPatchRequest.class)) {
            ReaderInterceptorContext invalidContext = context(type, cause);
            ApiResponseException failure = assertThrows(ApiResponseException.class,
                    () -> reader.aroundReadFrom(invalidContext));
            assertEquals("bad-request", failure.getResponseCode().getCode());
            assertNull(failure.getCause());
            ReaderInterceptorContext validContext = context(type, null);
            assertNull(reader.aroundReadFrom(validContext));
        }
        ReaderInterceptorContext unsupportedContext = context(LegalEntityCreateRequest.class, cause);
        assertSame(cause, assertThrows(JsonProcessingException.class,
                () -> reader.aroundReadFrom(unsupportedContext)));
    }

    private LegalEntityPutRequest validatePut(LegalEntityPutRequest request) {
        return support.validateBody(request, LegalEntityPutRequest::normalizedForValidation);
    }

    private LegalEntityPatchRequest validatePatch(LegalEntityPatchRequest request) {
        return support.validateBody(request, LegalEntityPatchRequest::normalizedForValidation);
    }

    private static void assertCode(String code, Runnable action) {
        ApiResponseException failure = assertThrows(ApiResponseException.class, action::run);
        assertEquals(400, failure.getResponseCode().getStatus());
        assertEquals(code, failure.getResponseCode().getCode());
    }

    private static ReaderInterceptorContext context(Class<?> type, JsonProcessingException failure) {
        return (ReaderInterceptorContext) Proxy.newProxyInstance(ReaderInterceptorContext.class.getClassLoader(),
                new Class<?>[] { ReaderInterceptorContext.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getType"))
                        return type;
                    if (method.getName().equals("proceed")) {
                        if (failure != null)
                            throw failure;
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
