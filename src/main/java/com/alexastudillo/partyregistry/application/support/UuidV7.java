package com.alexastudillo.partyregistry.application.support;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Generates time-ordered UUIDv7 identifiers for new party aggregates.
 *
 * <p>
 * The layout follows RFC 9562: a 48-bit millisecond timestamp, version and
 * variant markers, and cryptographically random payload bits.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long VERSION_BITS = 0x7000L;
    private static final long MAX_TIMESTAMP = 0xFFFFFFFFFFFFL;
    private static final long RANDOM_A_MASK = 0x0FFFL;
    private static final long RANDOM_B_MASK = 0x3FFFFFFFFFFFFFFFL;
    private static final long VARIANT_BITS = 0x8000000000000000L;

    private UuidV7() {
    }

    /**
     * Generates a new UUIDv7 value.
     *
     * @param occurredAt timestamp encoded in the identifier
     * @return a time-ordered version 7 UUID
     * @throws IllegalArgumentException when the timestamp is outside the UUIDv7
     *                                  range
     */
    public static UUID generate(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt");
        long timestamp;
        try {
            timestamp = occurredAt.toEpochMilli();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "UUIDv7 timestamp is outside the 48-bit range",
                    exception);
        }
        if (timestamp < 0 || timestamp > MAX_TIMESTAMP) {
            throw new IllegalArgumentException("UUIDv7 timestamp is outside the 48-bit range");
        }
        long mostSignificant = (timestamp << 16) | VERSION_BITS | (RANDOM.nextLong() & RANDOM_A_MASK);
        long leastSignificant = (RANDOM.nextLong() & RANDOM_B_MASK) | VARIANT_BITS;
        return new UUID(mostSignificant, leastSignificant);
    }
}
