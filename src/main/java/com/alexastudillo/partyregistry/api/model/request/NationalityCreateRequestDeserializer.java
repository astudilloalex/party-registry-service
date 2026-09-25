package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.io.IOException;
import java.io.Serial;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** Binds only the four approved create fields, rejecting duplicate and ill-typed JSON. */
@RegisterForReflection
public final class NationalityCreateRequestDeserializer extends StdDeserializer<NationalityCreateRequest> {

    @Serial
    private static final long serialVersionUID = 1L;

    public NationalityCreateRequestDeserializer() {
        super(NationalityCreateRequest.class);
    }

    @Override
    public NationalityCreateRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken()) {
            throw NationalityJsonFields.invalid(parser);
        }
        Set<String> seen = new HashSet<>();
        String countryCode = null;
        Boolean isPrimary = null;
        LocalDate validFrom = null;
        LocalDate validUntil = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME) || !seen.add(parser.currentName())) {
                throw NationalityJsonFields.invalid(parser);
            }
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "countryCode" -> countryCode = NationalityJsonFields.country(parser);
                case "isPrimary" -> {
                    if (!parser.hasToken(JsonToken.VALUE_TRUE) && !parser.hasToken(JsonToken.VALUE_FALSE)) {
                        throw NationalityJsonFields.invalid(parser);
                    }
                    isPrimary = parser.getBooleanValue();
                }
                case "validFrom" -> validFrom = NationalityJsonFields.date(parser);
                case "validUntil" -> validUntil = NationalityJsonFields.date(parser);
                default -> throw NationalityJsonFields.invalid(parser);
            }
        }
        NationalityJsonFields.finish(parser);
        return new NationalityCreateRequest(countryCode, Boolean.TRUE.equals(isPrimary), validFrom, validUntil);
    }
}
