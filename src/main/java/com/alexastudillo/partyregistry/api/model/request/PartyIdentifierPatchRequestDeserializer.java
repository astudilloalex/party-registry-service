package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
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

/** Decodes only three identifier metadata properties with exact omitted/present-null state and scalar/calendar syntax. */
@RegisterForReflection
public final class PartyIdentifierPatchRequestDeserializer extends StdDeserializer<PartyIdentifierPatchRequest> {
    @Serial private static final long serialVersionUID = 1L;
    public PartyIdentifierPatchRequestDeserializer() { super(PartyIdentifierPatchRequest.class); }

    @Override
    public PartyIdentifierPatchRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken()) { throw PartyIdentifierJsonFields.invalid(parser); }
        Set<String> seen = new HashSet<>();
        FieldUpdate<String> issuer = FieldUpdate.absent();
        FieldUpdate<LocalDate> issued = FieldUpdate.absent();
        FieldUpdate<LocalDate> expires = FieldUpdate.absent();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME) || !seen.add(parser.currentName())) { throw PartyIdentifierJsonFields.invalid(parser); }
            String field = parser.currentName(); parser.nextToken();
            switch (field) {
                case "issuerCode" -> issuer = FieldUpdate.present(PartyIdentifierJsonFields.text(parser));
                case "issuedOn" -> issued = FieldUpdate.present(PartyIdentifierJsonFields.date(parser));
                case "expiresOn" -> expires = FieldUpdate.present(PartyIdentifierJsonFields.date(parser));
                default -> throw PartyIdentifierJsonFields.invalid(parser);
            }
        }
        PartyIdentifierJsonFields.finish(parser);
        return new PartyIdentifierPatchRequest(issuer, issued, expires);
    }

    /** Leaves literal null to required-body validation without accepting a second root value. */
    @Override
    public @Nullable PartyIdentifierPatchRequest getNullValue(DeserializationContext context) throws JsonMappingException {
        var parser = context.getParser();
        try { PartyIdentifierJsonFields.finish(parser); return null; }
        catch (IOException _) { throw PartyIdentifierJsonFields.invalid(parser); }
    }
}
