package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Reads exact identifier JSON scalar/calendar tokens without normalizing raw input or applying business validity. */
final class PartyIdentifierJsonFields {
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private PartyIdentifierJsonFields() { }

    static @Nullable String text(JsonParser parser) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_NULL)) { return null; }
        if (!parser.hasToken(JsonToken.VALUE_STRING)) { throw invalid(parser); }
        return parser.getText();
    }

    static boolean bool(JsonParser parser) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_TRUE) && !parser.hasToken(JsonToken.VALUE_FALSE)) { throw invalid(parser); }
        return parser.getBooleanValue();
    }

    static @Nullable LocalDate date(JsonParser parser) throws IOException {
        String value = text(parser);
        if (value == null) { return null; }
        if (!ISO_DATE.matcher(value).matches()) { throw invalid(parser); }
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException _) { throw invalid(parser); }
    }

    static void finish(JsonParser parser) throws IOException {
        if (parser.getParsingContext().inRoot() && parser.nextToken() != null) { throw invalid(parser); }
    }

    static MismatchedInputException invalid(JsonParser parser) {
        return MismatchedInputException.from(parser, PartyIdentifierCreateRequest.class, "Invalid identifier JSON body");
    }
}
