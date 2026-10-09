package com.alexastudillo.partyregistry.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies bounded, exact UTF-16 audit storage including null, empty, controls and unmatched surrogate code units. */
class PartyIdentifierReasonCodecTest {

    @Test
    void preservesAbsentEmptyAndExactStringsWithoutCharsetReplacement() {
        assertNull(PartyIdentifierReasonCodec.encode(null, 64));
        assertNull(PartyIdentifierReasonCodec.decode(null, 64));
        for (String value : List.of("", " Code ", "\u0000\r\n\t\u0085", "\ud83d\ude00", "\ud800x\udfff")) {
            assertEquals(value, PartyIdentifierReasonCodec.decode(PartyIdentifierReasonCodec.encode(value, 64), 64));
        }
    }

    @Test
    void pinsIndependentBigEndianCodeUnitBytes() {
        byte[] expected = {0, 65, 0, 0, (byte) 0xd8, 0x3d, (byte) 0xde, 0};
        assertArrayEquals(expected, PartyIdentifierReasonCodec.encode("A\u0000\ud83d\ude00", 64));
        assertEquals("A\u0000\ud83d\ude00", PartyIdentifierReasonCodec.decode(expected, 64));
    }

    @Test
    void acceptsMaximumCodePointsAndRejectsLogicalOverflow() {
        String code = "\ud83d\ude00".repeat(64);
        String detail = "\ud83d\ude00".repeat(1000);
        assertEquals(256, Objects.requireNonNull(PartyIdentifierReasonCodec.encode(code, 64)).length);
        assertEquals(4000, Objects.requireNonNull(PartyIdentifierReasonCodec.encode(detail, 1000)).length);
        assertEquals(detail, PartyIdentifierReasonCodec.decode(PartyIdentifierReasonCodec.encode(detail, 1000), 1000));
        String tooLong = code + "x";
        assertThrows(IllegalArgumentException.class, () -> PartyIdentifierReasonCodec.encode(tooLong, 64));
    }

    @Test
    void rejectsMalformedAndOversizedStoredPayloadWithoutContentOrCause() {
        for (byte[] bytes : List.of(new byte[]{65}, new byte[258], new byte[130])) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> PartyIdentifierReasonCodec.decode(bytes, 64));
            assertEquals("Stored identifier audit reason is invalid", failure.getMessage());
            assertNull(failure.getCause());
        }
    }
}
