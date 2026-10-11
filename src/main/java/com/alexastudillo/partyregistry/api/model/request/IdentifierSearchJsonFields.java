package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Reads genuinely shared search token rules using raw UTF-16 bounds and fixed non-value-bearing diagnostics. */
final class IdentifierSearchJsonFields {
    private IdentifierSearchJsonFields() { }

    static String text(JsonParser parser, int maximum) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) throw invalid(parser);
        String value = parser.getText();
        if (value.isBlank() || value.length() > maximum) throw invalid(parser);
        return value;
    }

    static String required(JsonParser parser, @Nullable String value) throws IOException {
        if (value == null) throw invalid(parser);
        return value;
    }

    static <E extends Enum<E>> List<E> states(JsonParser parser, Class<E> type) throws IOException {
        if (!parser.hasToken(JsonToken.START_ARRAY)) throw invalid(parser);
        var values = new ArrayList<E>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) throw invalid(parser);
            try {
                values.add(Enum.valueOf(type, parser.getText()));
            } catch (IllegalArgumentException _) {
                throw invalid(parser);
            }
        }
        return List.copyOf(values);
    }

    static int limit(JsonParser parser) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) throw invalid(parser);
        BigInteger value = parser.getBigIntegerValue();
        if (value.compareTo(BigInteger.ONE) < 0 || value.compareTo(BigInteger.valueOf(200)) > 0) throw invalid(parser);
        return value.intValueExact();
    }

    static void finish(JsonParser parser) throws IOException {
        if (parser.getParsingContext().inRoot() && parser.nextToken() != null) throw invalid(parser);
    }

    static MismatchedInputException invalid(JsonParser parser) {
        return MismatchedInputException.from(parser, Object.class, "Invalid identifier search JSON body");
    }
}
