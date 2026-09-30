package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.model.request.LegalEntityPatchRequest;
import com.alexastudillo.partyregistry.api.model.request.LegalEntityPutRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

import java.io.IOException;
import java.util.Set;

/**
 * Translates only legal-update JSON binding failures into the shared sanitized
 * request-error contract.
 */
@Provider
@ConstrainedTo(RuntimeType.SERVER)
public class LegalEntityUpdateJsonReaderInterceptor implements ReaderInterceptor {

    private static final Set<Class<?>> REQUEST_TYPES = Set.of(LegalEntityPutRequest.class,
            LegalEntityPatchRequest.class);
    private static final System.Logger LOGGER = System
            .getLogger(LegalEntityUpdateJsonReaderInterceptor.class.getName());

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException {
        if (!REQUEST_TYPES.contains(context.getType())) {
            return context.proceed();
        }
        try {
            return context.proceed();
        } catch (JsonProcessingException _) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Request rejected status=400 code=bad-request source=json-binding resource=LegalEntityResource rule=invalid-json-value");
            throw new ApiResponseException(PartyResponseCode.BAD_REQUEST);
        }
    }
}
