package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
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

/** Decodes the closed creation object, retaining arbitrary integral bounds for Application. */
@RegisterForReflection
public final class IdentifierSchemeCreateRequestDeserializer extends StdDeserializer<IdentifierSchemeCreateRequest> {
    @Serial
    private static final long serialVersionUID = 1L;

    public IdentifierSchemeCreateRequestDeserializer() {
        super(IdentifierSchemeCreateRequest.class);
    }

    @Override
    public IdentifierSchemeCreateRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken()) {
            throw IdentifierSchemeJsonFields.invalid(parser);
        }
        Set<String> seen = new HashSet<>();
        String code = null;
        String country = null;
        IdentifierCategory category = null;
        IdentifierSubjectType subject = null;
        String name = null;
        String description = null;
        String normalizer = null;
        String validator = null;
        BigInteger minimum = null;
        BigInteger maximum = null;
        boolean expiration = false;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME) || !seen.add(parser.currentName())) {
                throw IdentifierSchemeJsonFields.invalid(parser);
            }
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "code" -> code = IdentifierSchemeJsonFields.text(parser);
                case "issuingCountryCode" -> country = IdentifierSchemeJsonFields.text(parser);
                case "category" -> category = IdentifierSchemeJsonFields.enumeration(parser, IdentifierCategory.class);
                case "applicableSubjectType" -> subject = IdentifierSchemeJsonFields.enumeration(parser, IdentifierSubjectType.class);
                case "name" -> name = IdentifierSchemeJsonFields.text(parser);
                case "description" -> description = IdentifierSchemeJsonFields.text(parser);
                case "normalizerKey" -> normalizer = IdentifierSchemeJsonFields.text(parser);
                case "validatorKey" -> validator = IdentifierSchemeJsonFields.text(parser);
                case "minimumLength" -> minimum = IdentifierSchemeJsonFields.integer(parser);
                case "maximumLength" -> maximum = IdentifierSchemeJsonFields.integer(parser);
                case "requiresExpiration" -> expiration = IdentifierSchemeJsonFields.bool(parser);
                default -> throw IdentifierSchemeJsonFields.invalid(parser);
            }
        }
        IdentifierSchemeJsonFields.finish(parser);
        return new IdentifierSchemeCreateRequest(code, country, category, subject, name, description,
                normalizer, validator, minimum, maximum, expiration);
    }
}
