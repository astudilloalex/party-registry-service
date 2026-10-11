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

/** Decodes prefix search while retaining omission/null distinctions and exact integer-token page capacity. */
@RegisterForReflection
public final class IdentifierSearchPrefixRequestDeserializer extends StdDeserializer<IdentifierSearchPrefixRequest> {
    @Serial private static final long serialVersionUID = 1L;
    public IdentifierSearchPrefixRequestDeserializer() { super(IdentifierSearchPrefixRequest.class); }

    @Override
    public IdentifierSearchPrefixRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken()) throw IdentifierSearchJsonFields.invalid(parser);
        var seen = new HashSet<String>();
        String code = null;
        String prefix = null;
        String cursor = null;
        List<PartyIdentifierStatus> identifiers = null;
        List<PartyRecordStatus> parties = null;
        int limit = 50;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME) || !seen.add(parser.currentName())) throw IdentifierSearchJsonFields.invalid(parser);
            String name = parser.currentName();
            parser.nextToken();
            switch (name) {
                case "identifierSchemeCode" -> code = IdentifierSearchJsonFields.text(parser, 64);
                case "prefix" -> prefix = IdentifierSearchJsonFields.text(parser, 64);
                case "identifierStatuses" -> identifiers = IdentifierSearchJsonFields.states(parser, PartyIdentifierStatus.class);
                case "partyRecordStatuses" -> parties = IdentifierSearchJsonFields.states(parser, PartyRecordStatus.class);
                case "limit" -> limit = IdentifierSearchJsonFields.limit(parser);
                case "cursor" -> cursor = parser.hasToken(JsonToken.VALUE_NULL) ? null : IdentifierSearchJsonFields.text(parser, 256);
                default -> throw IdentifierSearchJsonFields.invalid(parser);
            }
        }
        IdentifierSearchJsonFields.finish(parser);
        return new IdentifierSearchPrefixRequest(IdentifierSearchJsonFields.required(parser, code), IdentifierSearchJsonFields.required(parser, prefix),
                identifiers, parties, limit, cursor);
    }
}
