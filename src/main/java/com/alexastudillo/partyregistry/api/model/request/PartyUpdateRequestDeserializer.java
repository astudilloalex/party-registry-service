package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.io.IOException;
import java.io.Serial;

/** Rejects unknown/duplicate properties, scalar coercion, and trailing root content only for the root Party PATCH model. */
@RegisterForReflection
public final class PartyUpdateRequestDeserializer extends StdDeserializer<PartyUpdateRequest> {

    @Serial
    private static final long serialVersionUID = 1L;

    public PartyUpdateRequestDeserializer() {
        super(PartyUpdateRequest.class);
    }

    @Override
    public PartyUpdateRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken()) {
            throw invalid(parser);
        }
        boolean seen = false;
        String displayName = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME) || seen || !"displayName".equals(parser.currentName())) {
                throw invalid(parser);
            }
            seen = true;
            JsonToken value = parser.nextToken();
            if (value == JsonToken.VALUE_STRING) {
                displayName = parser.getText();
            } else if (value != JsonToken.VALUE_NULL) {
                throw invalid(parser);
            }
        }
        if (parser.getParsingContext().inRoot() && parser.nextToken() != null) {
            throw invalid(parser);
        }
        return new PartyUpdateRequest(displayName);
    }

    private static MismatchedInputException invalid(JsonParser parser) {
        return MismatchedInputException.from(parser, PartyUpdateRequest.class, "Invalid Party update body");
    }
}
