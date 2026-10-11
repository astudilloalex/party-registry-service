package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSearchExactRequest;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSearchPrefixRequest;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.core.NoContentException;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

import java.io.IOException;

/** Sanitizes only search binding failures into the published global error contract without retaining parser causes. */
@Provider
@ConstrainedTo(RuntimeType.SERVER)
public class IdentifierSearchJsonReaderInterceptor implements ReaderInterceptor {
    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException {
        if (context.getType() != IdentifierSearchExactRequest.class && context.getType() != IdentifierSearchPrefixRequest.class) return context.proceed();
        try {
            return context.proceed();
        } catch (NoContentException _) {
            throw new ApiResponseException(PartyResponseCode.REQUEST_BODY_REQUIRED);
        } catch (JsonParseException | MismatchedInputException | StreamConstraintsException _) {
            throw new ApiResponseException(PartyResponseCode.BAD_REQUEST);
        }
    }
}
