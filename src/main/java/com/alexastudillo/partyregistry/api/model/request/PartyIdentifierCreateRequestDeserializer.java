package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Serial;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** Decodes only closed identifier creation objects while retaining raw strings, nullable metadata and the false primary default. */
@RegisterForReflection
public final class PartyIdentifierCreateRequestDeserializer extends StdDeserializer<PartyIdentifierCreateRequest> {
    @Serial private static final long serialVersionUID = 1L;

    public PartyIdentifierCreateRequestDeserializer() { super(PartyIdentifierCreateRequest.class); }

    @Override
    public PartyIdentifierCreateRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken()) { throw PartyIdentifierJsonFields.invalid(parser); }
        Set<String> seen = new HashSet<>();
        String code = null;
        String value = null;
        String issuer = null;
        LocalDate issued = null;
        LocalDate expires = null;
        boolean primary = false;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME) || !seen.add(parser.currentName())) { throw PartyIdentifierJsonFields.invalid(parser); }
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "identifierSchemeCode" -> code = PartyIdentifierJsonFields.text(parser);
                case "value" -> value = PartyIdentifierJsonFields.text(parser);
                case "issuerCode" -> issuer = PartyIdentifierJsonFields.text(parser);
                case "issuedOn" -> issued = PartyIdentifierJsonFields.date(parser);
                case "expiresOn" -> expires = PartyIdentifierJsonFields.date(parser);
                case "isPrimary" -> primary = PartyIdentifierJsonFields.bool(parser);
                default -> throw PartyIdentifierJsonFields.invalid(parser);
            }
        }
        PartyIdentifierJsonFields.finish(parser);
        return new PartyIdentifierCreateRequest(code, value, issuer, issued, expires, primary);
    }

    /** Retains a required body's null value for API validation, while still rejecting trailing tokens after literal null. */
    @Override
    public @Nullable PartyIdentifierCreateRequest getNullValue(DeserializationContext context) throws JsonMappingException {
        var parser = context.getParser();
        try {
            PartyIdentifierJsonFields.finish(parser);
            return null;
        } catch (IOException _) {
            throw PartyIdentifierJsonFields.invalid(parser);
        }
    }
}
