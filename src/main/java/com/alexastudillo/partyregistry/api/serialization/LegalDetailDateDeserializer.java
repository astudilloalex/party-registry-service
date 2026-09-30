package com.alexastudillo.partyregistry.api.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.io.Serial;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Accepts only calendar-valid ISO date strings in legal-detail update bodies.
 */
public final class LegalDetailDateDeserializer extends StdDeserializer<LocalDate> {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    public LegalDetailDateDeserializer() {
        super(LocalDate.class);
    }

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING) || !ISO_DATE.matcher(parser.getText()).matches()) {
            return context.reportInputMismatch(LocalDate.class, "Expected an ISO date string");
        }
        try {
            return LocalDate.parse(parser.getText());
        } catch (DateTimeParseException _) {
            return context.reportInputMismatch(LocalDate.class, "Invalid calendar date");
        }
    }
}
