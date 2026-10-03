package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSchemeCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSchemePatchRequest;
import com.alexastudillo.partyregistry.api.model.request.NationalityCreateRequest;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.ws.rs.core.NoContentException;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies type-specific sanitized binding translation without touching streams
 * or unrelated DTOs.
 */
class IdentifierSchemeJsonReaderInterceptorTest {
    private final IdentifierSchemeJsonReaderInterceptor interceptor = new IdentifierSchemeJsonReaderInterceptor();

    @Test
    void translatesOnlyClientBindingFailuresForBothSchemeModels() {
        for (Class<?> type : List.of(IdentifierSchemeCreateRequest.class, IdentifierSchemePatchRequest.class)) {
            for (IOException failure : List.of(MismatchedInputException.from((JsonParser) null, type, "private body"),
                    new JsonParseException(null, "private JSON"), new NoContentException("private absence"))) {
                var context = context(type, () -> {
                    throw failure;
                });
                var translated = assertThrows(ApiResponseException.class,
                        () -> interceptor.aroundReadFrom(context));
                assertEquals(failure instanceof NoContentException ? PartyResponseCode.REQUEST_BODY_REQUIRED
                        : PartyResponseCode.BAD_REQUEST, translated.getResponseCode());
                assertNull(translated.getCause());
            }
        }
    }

    @Test
    void leavesSuccessfulNullUnrelatedDefinitionAndIoOutcomesUntouched() throws IOException {
        Object result = new Object();
        assertSame(result, interceptor.aroundReadFrom(context(IdentifierSchemeCreateRequest.class, () -> result)));
        assertNull(interceptor.aroundReadFrom(context(IdentifierSchemePatchRequest.class, () -> null)));
        IOException binding = MismatchedInputException.from((JsonParser) null, String.class, "private");
        for (Class<?> type : List.of(String.class, NationalityCreateRequest.class, byte[].class)) {
            var context = context(type, () -> {
                throw binding;
            });
            assertSame(binding, assertThrows(IOException.class,
                    () -> interceptor.aroundReadFrom(context)));
        }
        for (IOException failure : List.of(new IOException("private I/O"),
                InvalidDefinitionException.from((JsonParser) null, "private definition", (JavaType) null))) {
            var context = context(IdentifierSchemeCreateRequest.class, () -> {
                throw failure;
            });
            assertSame(failure, assertThrows(IOException.class, () -> interceptor.aroundReadFrom(context)));
        }
    }

    private static ReaderInterceptorContext context(Class<?> type, ReadOperation operation) {
        return ReaderInterceptorContext.class
                .cast(Proxy.newProxyInstance(ReaderInterceptorContext.class.getClassLoader(),
                        new Class<?>[] { ReaderInterceptorContext.class }, (_, method, _) -> switch (method.getName()) {
                            case "getType" -> type;
                            case "proceed" -> operation.read();
                            default -> throw new UnsupportedOperationException(method.getName());
                        }));
    }

    /** Supplies outcomes without implementing unused reader context operations. */
    private interface ReadOperation {
        Object read() throws IOException;
    }
}
