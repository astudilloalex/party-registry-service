package com.alexastudillo.partyregistry.api.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.io.Serial;

/** Rejects scalar coercion for strings in legal-detail update bodies only. */
public final class LegalDetailStringDeserializer extends StdDeserializer<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    public LegalDetailStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(String.class, "Expected a JSON string");
        }
        return parser.getText();
    }
}
