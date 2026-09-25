package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.model.request.NationalityCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.NationalityPatchRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

import java.io.IOException;

/** Converts only nationality JSON binding failures into the sanitized shared 400 envelope. */
@Provider
@ConstrainedTo(RuntimeType.SERVER)
public class NationalityJsonReaderInterceptor implements ReaderInterceptor {

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException {
        if (context.getType() != NationalityCreateRequest.class
                && context.getType() != NationalityPatchRequest.class) {
            return context.proceed();
        }
        try {
            return context.proceed();
        } catch (JsonProcessingException _) {
            throw new ApiResponseException(PartyResponseCode.BAD_REQUEST);
        }
    }
}
