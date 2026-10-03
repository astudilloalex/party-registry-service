package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigInteger;

/** Reads exact scheme scalar tokens without coercion or semantic configuration evaluation. */
final class IdentifierSchemeJsonFields {
    private IdentifierSchemeJsonFields() {
    }

    static @Nullable String text(JsonParser parser) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_NULL)) {
            return null;
        }
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            throw invalid(parser);
        }
        return parser.getText();
    }

    static @Nullable BigInteger integer(JsonParser parser) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_NULL)) {
            return null;
        }
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            throw invalid(parser);
        }
        return parser.getBigIntegerValue();
    }

    static boolean bool(JsonParser parser) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_TRUE) && !parser.hasToken(JsonToken.VALUE_FALSE)) {
            throw invalid(parser);
        }
        return parser.getBooleanValue();
    }

    static <E extends Enum<E>> @Nullable E enumeration(JsonParser parser, Class<E> type) throws IOException {
        String value = text(parser);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException _) {
            throw invalid(parser);
        }
    }

    static void finish(JsonParser parser) throws IOException {
        if (parser.getParsingContext().inRoot() && parser.nextToken() != null) {
            throw invalid(parser);
        }
    }

    static MismatchedInputException invalid(JsonParser parser) {
        return MismatchedInputException.from(parser, IdentifierSchemeCreateRequest.class,
                "Invalid identifier scheme JSON body");
    }
}
