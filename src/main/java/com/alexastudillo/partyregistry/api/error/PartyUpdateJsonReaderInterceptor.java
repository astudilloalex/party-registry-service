package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.model.request.PartyUpdateRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

import java.io.IOException;

/** Translates only root PATCH JSON binding failures without retaining parser causes or rejected source content. */
@Provider
@ConstrainedTo(RuntimeType.SERVER)
public class PartyUpdateJsonReaderInterceptor implements ReaderInterceptor {

    private static final System.Logger LOGGER = System.getLogger(PartyUpdateJsonReaderInterceptor.class.getName());

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException {
        if (context.getType() != PartyUpdateRequest.class) {
            return context.proceed();
        }
        try {
            return context.proceed();
        } catch (JsonProcessingException _) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Request rejected status=400 code=bad-request source=json-binding resource=PartyResource rule=invalid-json-value");
            throw new ApiResponseException(PartyResponseCode.BAD_REQUEST);
        }
    }
}
