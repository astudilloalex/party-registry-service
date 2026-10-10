package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierReasonRequest;
import com.alexastudillo.partyregistry.api.model.request.PartyIdentifierPrimaryRequest;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/** Owns action-body syntax only, using already buffered bytes and exact optional reason text. */
public final class PartyIdentifierActionBodies {
    private PartyIdentifierActionBodies() { }

    /** Requires zero actual body bytes without reading a transport stream or using Content-Length. */
    public static void verify(@Nullable byte[] body) {
        if (body != null && body.length != 0) { throw new ApiResponseException(PartyResponseCode.BAD_REQUEST); }
    }

    /** Maps absent body to omitted reasons and validates exact supplied code-point limits. */
    public static PartyIdentifierReasonRequest reasons(@Nullable PartyIdentifierReasonRequest request) {
        if (request == null) { return new PartyIdentifierReasonRequest(null, null); }
        if (exceeds(request.reasonCode(), 64) || exceeds(request.reasonDetail(), 1000)) { throw new ApiResponseException(PartyResponseCode.BAD_REQUEST); }
        return request;
    }

    /** Distinguishes actual zero bytes from whitespace/literal null before decoding only the buffered in-memory JSON. */
    public static PartyIdentifierReasonRequest reasons(@Nullable byte[] body, ObjectMapper json) {
        if (body == null || body.length == 0) { return new PartyIdentifierReasonRequest(null, null); }
        return reasons(readBuffered(body, json, PartyIdentifierReasonRequest.class));
    }

    private static <T> T readBuffered(byte[] body, ObjectMapper json, Class<T> type) {
        try { return json.readValue(body, type); }
        catch (JsonParseException | MismatchedInputException | StreamConstraintsException _) {
            throw new ApiResponseException(PartyResponseCode.BAD_REQUEST);
        } catch (IOException _) {
            // A server reader definition must not become a client failure or retain submitted action content.
            throw new IllegalStateException("Identifier action binding failed");
        }
    }

    private static boolean exceeds(@Nullable String value, int maximum) { return value != null && value.codePointCount(0, value.length()) > maximum; }

    /** Maps the optional buffered replacement body to its effective choice. */
    public static PartyIdentifierPrimaryRequest primary(@Nullable byte[] body, ObjectMapper json) {
        if (body == null || body.length == 0) { return new PartyIdentifierPrimaryRequest(false); }
        return readBuffered(body, json, PartyIdentifierPrimaryRequest.class);
    }
}
