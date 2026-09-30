package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonPatchRequest;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonPutRequest;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies narrowly scoped JSON failure translation without starting HTTP or persistence.
 */
class NaturalPersonJsonReaderInterceptorTest {

    private final NaturalPersonJsonReaderInterceptor interceptor = new NaturalPersonJsonReaderInterceptor();

    @Test
    void delegatesSuccessfulReadsUnchanged() throws IOException {
        Object result = new Object();
        assertSame(result, interceptor.aroundReadFrom(context(NaturalPersonCreateRequest.class, () -> result)));
    }

    @Test
    void translatesAllNaturalPersonBodyTypesWithoutRetainingSensitiveExceptionText() {
        for (Class<?> type : List.of(NaturalPersonCreateRequest.class,
                NaturalPersonPutRequest.class, NaturalPersonPatchRequest.class)) {
            MismatchedInputException failure = bindingFailure(type);
            ReaderInterceptorContext context = context(type, () -> { throw failure; });
            ApiResponseException translated = assertThrows(ApiResponseException.class,
                    () -> interceptor.aroundReadFrom(context));
            assertSame(PartyResponseCode.BAD_REQUEST, translated.getResponseCode());
            assertEquals(400, translated.getResponseCode().getStatus());
            assertNull(translated.getCause());
        }
    }

    @Test
    void leavesOtherBodyTypesUntouched() {
        MismatchedInputException failure = bindingFailure(String.class);
        assertSame(failure, assertThrows(MismatchedInputException.class,
                () -> interceptor.aroundReadFrom(context(String.class, () -> { throw failure; }))));
    }

    @Test
    void doesNotMisclassifyServerDefinitionOrIoFailures() {
        InvalidDefinitionException definitionFailure = InvalidDefinitionException.from(
                (JsonParser) null, "Server-side definition failure", (JavaType) null);
        IOException ioFailure = new IOException("Input stream failure");
        for (IOException failure : List.of(definitionFailure, ioFailure)) {
            assertSame(failure, assertThrows(IOException.class,
                    () -> interceptor.aroundReadFrom(context(NaturalPersonCreateRequest.class, () -> { throw failure; }))));
        }
    }

    private static MismatchedInputException bindingFailure(Class<?> type) {
        return MismatchedInputException.from((JsonParser) null, type, "Sensitive submitted value");
    }

    private static ReaderInterceptorContext context(Class<?> type, ReadOperation operation) {
        return (ReaderInterceptorContext) Proxy.newProxyInstance(ReaderInterceptorContext.class.getClassLoader(),
                new Class<?>[] { ReaderInterceptorContext.class }, (_, method, _) -> switch (method.getName()) {
                    case "getType" -> type;
                    case "proceed" -> operation.read();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * Supplies a reader outcome or failure without implementing unused context operations.
     */
    private interface ReadOperation {
        Object read() throws IOException;
    }
}
