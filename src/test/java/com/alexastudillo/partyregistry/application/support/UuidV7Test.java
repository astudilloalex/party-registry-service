package com.alexastudillo.partyregistry.application.support;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the identifiers generated for new party aggregates.
 */
class UuidV7Test {

    @Test
    void generatesUniqueRfc9562VersionSevenIdentifiersAtTheSuppliedTime() {
        Instant occurredAt = Instant.parse("2026-08-30T10:15:30Z");

        UUID first = UuidV7.generate(occurredAt);
        UUID second = UuidV7.generate(occurredAt);

        assertEquals(7, first.version());
        assertEquals(2, first.variant());
        assertEquals(occurredAt.toEpochMilli(), first.getMostSignificantBits() >>> 16);
        assertNotEquals(first, second);
    }

    @Test
    void rejectsTimestampsOutsideTheUuidV7Range() {
        Instant negativeTimestamp = Instant.ofEpochMilli(-1);
        assertThrows(IllegalArgumentException.class, () -> UuidV7.generate(negativeTimestamp));

        Instant excessTimestamp = Instant.ofEpochMilli(0x1000000000000L);
        assertThrows(IllegalArgumentException.class, () -> UuidV7.generate(excessTimestamp));

        assertThrows(IllegalArgumentException.class, () -> UuidV7.generate(Instant.MIN));
        assertThrows(IllegalArgumentException.class, () -> UuidV7.generate(Instant.MAX));
    }
}
