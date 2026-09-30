package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
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

/** Preserves field presence while rejecting unknown, duplicate, or ill-typed PATCH values. */
@RegisterForReflection
public final class NationalityPatchRequestDeserializer extends StdDeserializer<NationalityPatchRequest> {

    @Serial
    private static final long serialVersionUID = 1L;

    public NationalityPatchRequestDeserializer() {
        super(NationalityPatchRequest.class);
    }

    @Override
    public NationalityPatchRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.isExpectedStartObjectToken()) {
            throw NationalityJsonFields.invalid(parser);
        }
        Set<String> seen = new HashSet<>();
        FieldUpdate<LocalDate> from = FieldUpdate.absent();
        FieldUpdate<LocalDate> until = FieldUpdate.absent();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME) || !seen.add(parser.currentName())) {
                throw NationalityJsonFields.invalid(parser);
            }
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "validFrom" -> from = FieldUpdate.present(NationalityJsonFields.date(parser));
                case "validUntil" -> until = FieldUpdate.present(NationalityJsonFields.date(parser));
                default -> throw NationalityJsonFields.invalid(parser);
            }
        }
        NationalityJsonFields.finish(parser);
        return new NationalityPatchRequest(from, until);
    }
}
