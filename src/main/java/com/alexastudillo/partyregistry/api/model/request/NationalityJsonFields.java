package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Parses exact nationality JSON scalar tokens without Jackson scalar coercion. */
final class NationalityJsonFields {

    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private NationalityJsonFields() {
    }

    static String country(JsonParser parser) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_NULL)) {
            return null;
        }
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            throw invalid(parser);
        }
        return parser.getText();
    }

    static LocalDate date(JsonParser parser) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_NULL)) {
            return null;
        }
        if (!parser.hasToken(JsonToken.VALUE_STRING) || !ISO_DATE.matcher(parser.getText()).matches()) {
            throw invalid(parser);
        }
        try {
            return LocalDate.parse(parser.getText());
        } catch (DateTimeParseException _) {
            throw invalid(parser);
        }
    }

    static MismatchedInputException invalid(JsonParser parser) {
        return MismatchedInputException.from(parser, NationalityCreateRequest.class, "Invalid nationality JSON body");
    }

    static void finish(JsonParser parser) throws IOException {
        if (parser.getParsingContext().inRoot() && parser.nextToken() != null) {
            throw invalid(parser);
        }
    }
}
