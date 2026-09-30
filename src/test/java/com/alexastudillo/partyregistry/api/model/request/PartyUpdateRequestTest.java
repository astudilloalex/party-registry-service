package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.error.PartyUpdateJsonReaderInterceptor;
import com.alexastudillo.partyregistry.api.support.ApiRequestSupport;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies model-local strict binding and normalized structural checks without preempting post-version blank-name validation. */
class PartyUpdateRequestTest {

    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private final ApiRequestSupport support = new ApiRequestSupport(VALIDATORS.getValidator());
    private final ObjectMapper json = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @AfterAll
    static void closeFactory() {
        VALIDATORS.close();
    }

    @Test
    void distinguishesMissingBodyFromMissingOrNullProperty() throws Exception {
        assertCode(null, PartyResponseCode.REQUEST_BODY_REQUIRED);
        PartyUpdateRequest nullBody = json.readValue("null", PartyUpdateRequest.class);
        assertNull(nullBody);
        assertCode(nullBody, PartyResponseCode.REQUEST_BODY_REQUIRED);
        assertCode(json.readValue("{}", PartyUpdateRequest.class), PartyResponseCode.DISPLAY_NAME_REQUIRED);
        assertCode(json.readValue("{\"displayName\":null}", PartyUpdateRequest.class), PartyResponseCode.DISPLAY_NAME_REQUIRED);
    }

    @Test
    void preservesOriginalStringsAndAllowsBlankThroughStructuralValidation() throws Exception {
        for (String value : List.of("", " \t ", "  Compañía  Águila %_  ")) {
            String body = json.writeValueAsString(new PartyUpdateRequest(value));
            PartyUpdateRequest request = json.readValue(body, PartyUpdateRequest.class);
            assertSame(request, support.validateBody(request, PartyUpdateRequest::normalizedForValidation));
            assertEquals(value, request.displayName());
        }
        var request = new PartyUpdateRequest("  Compañía  Águila %_  ");
        assertEquals("COMPAÑÍA  ÁGUILA %_", request.normalizedForValidation().displayName());
    }

    @Test
    void boundsNormalizedUtf16IncludingExpansionAndExcludingExteriorWhitespace() {
        for (String valid : List.of("a".repeat(300), "ß".repeat(150), "🙂".repeat(150), " ".repeat(400) + "label")) {
            var request = new PartyUpdateRequest(valid);
            assertSame(request, support.validateBody(request, PartyUpdateRequest::normalizedForValidation));
        }
        for (String invalid : List.of("a".repeat(301), "ß".repeat(151), "🙂".repeat(151))) {
            assertCode(new PartyUpdateRequest(invalid), PartyResponseCode.DISPLAY_NAME_TOO_LONG);
        }
    }

    @Test
    void rejectsUnknownDuplicateAndCoercedInputIndependentlyOfGlobalMapperOptions() {
        for (String body : List.of("[]", "42", "true", "\"body\"", "{", "{\"displayName\":42}",
                "{\"displayName\":true}", "{\"displayName\":[]}", "{\"displayName\":{}}",
                "{\"displayName\":\"First\",\"displayName\":\"Second\"}",
                "{\"displayName\":null,\"displayName\":null}",
                "{\"displayName\":\"Valid\",\"recordStatus\":\"ACTIVE\"}",
                "{\"displayName\":\"Valid\",\"naturalPersonDetails\":{}}",
                "{\"displayName\":\"Valid\",\"normalizedForValidation\":true}",
                "{\"displayName\":\"Valid\"} {}", "{\"displayName\":\"Valid\"} trailing")) {
            assertThrows(JsonProcessingException.class, () -> json.readValue(body, PartyUpdateRequest.class));
        }
    }

    @Test
    void scopesReaderTranslationAndDropsOnlyJsonParserCauses() throws Exception {
        var interceptor = new PartyUpdateJsonReaderInterceptor();
        var malformed = new JsonParseException((JsonParser) null, "private-source-content");
        ReaderInterceptorContext root = context(PartyUpdateRequest.class, malformed);
        var translated = assertThrows(ApiResponseException.class, () -> interceptor.aroundReadFrom(root));
        assertEquals(PartyResponseCode.BAD_REQUEST, translated.getResponseCode());
        assertNull(translated.getCause());
        ReaderInterceptorContext unrelated = context(String.class, malformed);
        assertSame(malformed, assertThrows(JsonProcessingException.class, () -> interceptor.aroundReadFrom(unrelated)));
        var transport = new IOException("Controlled transport failure");
        ReaderInterceptorContext failed = context(PartyUpdateRequest.class, transport);
        assertSame(transport, assertThrows(IOException.class, () -> interceptor.aroundReadFrom(failed)));
        assertNull(interceptor.aroundReadFrom(context(PartyUpdateRequest.class, null)));
    }

    private void assertCode(PartyUpdateRequest request, PartyResponseCode code) {
        var failure = assertThrows(ApiResponseException.class, () -> support.validateBody(request, PartyUpdateRequest::normalizedForValidation));
        assertEquals(code, failure.getResponseCode());
    }

    private static ReaderInterceptorContext context(Class<?> type, IOException failure) {
        Object proxy = Proxy.newProxyInstance(ReaderInterceptorContext.class.getClassLoader(), new Class<?>[]{ReaderInterceptorContext.class},
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "getType" -> type;
                    case "proceed" -> {
                        if (failure != null) {
                            throw failure;
                        }
                        yield null;
                    }
                    default -> throw new AssertionError("Unexpected reader operation: " + method.getName());
                });
        return ReaderInterceptorContext.class.cast(proxy);
    }
}
