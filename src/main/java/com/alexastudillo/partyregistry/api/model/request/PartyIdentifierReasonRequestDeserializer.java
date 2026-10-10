package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.io.IOException;
import java.io.Serial;
import java.util.HashSet;
import java.util.Set;

/** Decodes a closed optional reason object, retaining exact strings but rejecting every explicit null and coercion. */
@RegisterForReflection
public final class PartyIdentifierReasonRequestDeserializer extends StdDeserializer<PartyIdentifierReasonRequest> {
    @Serial private static final long serialVersionUID = 1L;
    public PartyIdentifierReasonRequestDeserializer() { super(PartyIdentifierReasonRequest.class); }

    @Override
    public PartyIdentifierReasonRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken()) { throw PartyIdentifierJsonFields.invalid(parser); }
        Set<String> seen = new HashSet<>();
        String code = null;
        String detail = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME) || !seen.add(parser.currentName())) { throw PartyIdentifierJsonFields.invalid(parser); }
            String field = parser.currentName(); parser.nextToken();
            if (parser.hasToken(JsonToken.VALUE_NULL)) { throw PartyIdentifierJsonFields.invalid(parser); }
            switch (field) {
                case "reasonCode" -> code = PartyIdentifierJsonFields.text(parser);
                case "reasonDetail" -> detail = PartyIdentifierJsonFields.text(parser);
                default -> throw PartyIdentifierJsonFields.invalid(parser);
            }
        }
        PartyIdentifierJsonFields.finish(parser);
        return new PartyIdentifierReasonRequest(code, detail);
    }

    /** Explicit JSON null never acquires the absent-body default. */
    @Override
    public PartyIdentifierReasonRequest getNullValue(DeserializationContext context) throws JsonMappingException {
        throw PartyIdentifierJsonFields.invalid(context.getParser());
    }
}
