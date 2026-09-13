package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonPatchRequest;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonPutRequest;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.PropertyBindingException;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

import java.io.IOException;
import java.util.Set;

/**
 * Translates natural-person JSON binding failures for the shared global error handler.
 */
@Provider
@ConstrainedTo(RuntimeType.SERVER)
public class NaturalPersonJsonReaderInterceptor implements ReaderInterceptor {

    private static final System.Logger LOGGER = System.getLogger(NaturalPersonJsonReaderInterceptor.class.getName());
    private static final Set<Class<?>> REQUEST_TYPES = Set.of(
            NaturalPersonCreateRequest.class, NaturalPersonPutRequest.class, NaturalPersonPatchRequest.class);

    /**
     * Observes binding failures without replacing the JSON reader or producing HTTP responses.
     */
    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException {
        if (!REQUEST_TYPES.contains(context.getType())) {
            return context.proceed();
        }
        try {
            return context.proceed();
        } catch (MismatchedInputException failure) {
            logRejection(failure);
            throw new ApiResponseException(PartyResponseCode.BAD_REQUEST);
        }
    }

    private static void logRejection(MismatchedInputException failure) {
        var path = failure.getPath();
        boolean unknownProperty = failure instanceof PropertyBindingException;
        StringBuilder field = new StringBuilder("body");
        // Unknown property names are client-controlled and may themselves contain sensitive data.
        int knownPathLength = unknownProperty ? Math.max(0, path.size() - 1) : path.size();
        for (int index = 0; index < knownPathLength; index++) {
            String name = path.get(index).getFieldName();
            if (name != null) {
                field.append('.').append(name);
            }
        }
        if (unknownProperty) {
            field.append(".<unknown>");
        }
        var location = failure.getLocation();
        LOGGER.log(System.Logger.Level.WARNING,
                "Request rejected status=400 code={0} source=json-binding resource=NaturalPersonResource "
                        + "field={1} rule={2} exceptionType={3} line={4} column={5}",
                PartyResponseCode.BAD_REQUEST.getCode(), field.toString(),
                unknownProperty ? "unknown-property" : "invalid-json-value",
                failure.getClass().getSimpleName(),
                location == null ? -1 : location.getLineNr(),
                location == null ? -1 : location.getColumnNr());
    }
}
