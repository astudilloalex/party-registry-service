package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.io.IOException;
import java.io.Serial;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

/** Decodes only permitted PATCH tokens while preserving omission, clearing, and exact integral values. */
@RegisterForReflection
public final class IdentifierSchemePatchRequestDeserializer extends StdDeserializer<IdentifierSchemePatchRequest> {
    @Serial
    private static final long serialVersionUID = 1L;

    public IdentifierSchemePatchRequestDeserializer() {
        super(IdentifierSchemePatchRequest.class);
    }

    @Override
    public IdentifierSchemePatchRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken()) {
            throw IdentifierSchemeJsonFields.invalid(parser);
        }
        Set<String> seen = new HashSet<>();
        FieldUpdate<String> name = FieldUpdate.absent();
        FieldUpdate<String> description = FieldUpdate.absent();
        FieldUpdate<String> normalizer = FieldUpdate.absent();
        FieldUpdate<String> validator = FieldUpdate.absent();
        FieldUpdate<BigInteger> minimum = FieldUpdate.absent();
        FieldUpdate<BigInteger> maximum = FieldUpdate.absent();
        FieldUpdate<Boolean> expiration = FieldUpdate.absent();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME) || !seen.add(parser.currentName())) {
                throw IdentifierSchemeJsonFields.invalid(parser);
            }
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "name" -> name = FieldUpdate.present(IdentifierSchemeJsonFields.text(parser));
                case "description" -> description = FieldUpdate.present(IdentifierSchemeJsonFields.text(parser));
                case "normalizerKey" -> normalizer = FieldUpdate.present(IdentifierSchemeJsonFields.text(parser));
                case "validatorKey" -> validator = FieldUpdate.present(IdentifierSchemeJsonFields.text(parser));
                case "minimumLength" -> minimum = FieldUpdate.present(IdentifierSchemeJsonFields.integer(parser));
                case "maximumLength" -> maximum = FieldUpdate.present(IdentifierSchemeJsonFields.integer(parser));
                case "requiresExpiration" -> expiration = FieldUpdate.present(parser.hasToken(JsonToken.VALUE_NULL)
                        ? null : IdentifierSchemeJsonFields.bool(parser));
                default -> throw IdentifierSchemeJsonFields.invalid(parser);
            }
        }
        IdentifierSchemeJsonFields.finish(parser);
        return new IdentifierSchemePatchRequest(name, description, normalizer, validator, minimum, maximum, expiration);
    }
}
