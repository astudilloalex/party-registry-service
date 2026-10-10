package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.io.IOException;
import java.io.Serial;

/** Decodes only an optional strict replacement boolean, rejecting nulls, duplicates, coercions and trailing input. */
@RegisterForReflection
public final class PartyIdentifierPrimaryRequestDeserializer extends StdDeserializer<PartyIdentifierPrimaryRequest> {
    @Serial private static final long serialVersionUID = 1L;

    public PartyIdentifierPrimaryRequestDeserializer() { super(PartyIdentifierPrimaryRequest.class); }

    @Override
    public PartyIdentifierPrimaryRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken()) { throw PartyIdentifierJsonFields.invalid(parser); }
        boolean seen = false;
        boolean replace = false;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME) || seen || !"replaceCurrentPrimary".equals(parser.currentName())) {
                throw PartyIdentifierJsonFields.invalid(parser);
            }
            seen = true;
            parser.nextToken();
            replace = PartyIdentifierJsonFields.bool(parser);
        }
        PartyIdentifierJsonFields.finish(parser);
        return new PartyIdentifierPrimaryRequest(replace);
    }

    /** Explicit JSON null is invalid rather than an absent-body default. */
    @Override
    public PartyIdentifierPrimaryRequest getNullValue(DeserializationContext context) throws JsonMappingException {
        throw PartyIdentifierJsonFields.invalid(context.getParser());
    }
}
