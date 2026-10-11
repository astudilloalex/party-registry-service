package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.io.IOException;
import java.io.Serial;
import java.util.HashSet;
import java.util.List;

/** Decodes only exact-search properties without coercion, ambiguous duplicates or pagination inputs. */
@RegisterForReflection
public final class IdentifierSearchExactRequestDeserializer extends StdDeserializer<IdentifierSearchExactRequest> {
    @Serial private static final long serialVersionUID = 1L;
    public IdentifierSearchExactRequestDeserializer() { super(IdentifierSearchExactRequest.class); }

    @Override
    public IdentifierSearchExactRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken()) throw IdentifierSearchJsonFields.invalid(parser);
        var seen = new HashSet<String>();
        String code = null;
        String value = null;
        List<PartyIdentifierStatus> identifiers = null;
        List<PartyRecordStatus> parties = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME) || !seen.add(parser.currentName())) throw IdentifierSearchJsonFields.invalid(parser);
            String name = parser.currentName();
            parser.nextToken();
            switch (name) {
                case "identifierSchemeCode" -> code = IdentifierSearchJsonFields.text(parser, 64);
                case "value" -> value = IdentifierSearchJsonFields.text(parser, 256);
                case "identifierStatuses" -> identifiers = IdentifierSearchJsonFields.states(parser, PartyIdentifierStatus.class);
                case "partyRecordStatuses" -> parties = IdentifierSearchJsonFields.states(parser, PartyRecordStatus.class);
                default -> throw IdentifierSearchJsonFields.invalid(parser);
            }
        }
        IdentifierSearchJsonFields.finish(parser);
        return new IdentifierSearchExactRequest(IdentifierSearchJsonFields.required(parser, code), IdentifierSearchJsonFields.required(parser, value), identifiers, parties);
    }
}
