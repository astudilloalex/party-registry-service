package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierPatchRequest;
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

/** Translates creation/metadata-PATCH client binding failures without retaining submitted content or parser causes. */
@Provider
@ConstrainedTo(RuntimeType.SERVER)
public class PartyIdentifierJsonReaderInterceptor implements ReaderInterceptor {
    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException {
        if (context.getType() != PartyIdentifierCreateRequest.class && context.getType() != PartyIdentifierPatchRequest.class) { return context.proceed(); }
        try {
            return context.proceed();
        } catch (NoContentException _) {
            throw new ApiResponseException(PartyResponseCode.REQUEST_BODY_REQUIRED);
        } catch (JsonParseException | MismatchedInputException | StreamConstraintsException _) {
            throw new ApiResponseException(PartyResponseCode.BAD_REQUEST);
        }
    }
}
