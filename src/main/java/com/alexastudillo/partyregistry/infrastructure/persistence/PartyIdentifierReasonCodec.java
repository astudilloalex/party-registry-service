package com.alexastudillo.partyregistry.infrastructure.persistence;

import org.jspecify.annotations.Nullable;

/** Preserves private audit strings as explicit big-endian UTF-16 code units without lossy charset conversion. */
final class PartyIdentifierReasonCodec {

    private PartyIdentifierReasonCodec() { }

    static byte @Nullable [] encode(@Nullable String value, int maximumCodePoints) {
        if (value == null) { return null; }
        if (value.codePointCount(0, value.length()) > maximumCodePoints) {
            throw new IllegalArgumentException("Identifier audit reason exceeds its bound");
        }
        byte[] encoded = new byte[value.length() * 2];
        for (int index = 0; index < value.length(); index++) {
            char unit = value.charAt(index);
            encoded[index * 2] = (byte) (unit >>> 8);
            encoded[index * 2 + 1] = (byte) unit;
        }
        return encoded;
    }

    static @Nullable String decode(byte @Nullable [] encoded, int maximumCodePoints) {
        if (encoded == null) { return null; }
        if (encoded.length % 2 != 0 || encoded.length > maximumCodePoints * 4) { throw invalid(); }
        char[] units = new char[encoded.length / 2];
        for (int index = 0; index < units.length; index++) {
            units[index] = (char) ((encoded[index * 2] & 0xff) << 8 | encoded[index * 2 + 1] & 0xff);
        }
        String value = new String(units);
        if (value.codePointCount(0, value.length()) > maximumCodePoints) { throw invalid(); }
        return value;
    }

    private static IllegalStateException invalid() { return new IllegalStateException("Stored identifier audit reason is invalid"); }
}
