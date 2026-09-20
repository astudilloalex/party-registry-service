package com.alexastudillo.partyregistry.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Prevents root mutation configuration from silently disabling or overflowing PostgreSQL wait limits. */
class PartyMutationTimeoutsTest {

    @Test
    void acceptsPositiveFiniteBudgets() {
        var configuration = new Budgets(Duration.ofMillis(1), Duration.ofSeconds(2), Duration.ofSeconds(5));
        assertDoesNotThrow(() -> new PartyMutationTimeouts(configuration));
    }

    @Test
    void rejectsDisabledNegativeSubmillisecondAndUnsupportedServerLimits() {
        Duration valid = Duration.ofSeconds(1);
        for (Duration invalid : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofNanos(1),
                Duration.ofMillis(Integer.MAX_VALUE + 1L))) {
            var lock = new Budgets(invalid, valid, valid);
            var statement = new Budgets(valid, invalid, valid);
            assertThrows(IllegalArgumentException.class, () -> new PartyMutationTimeouts(lock));
            assertThrows(IllegalArgumentException.class, () -> new PartyMutationTimeouts(statement));
        }
        for (Duration invalid : List.of(Duration.ZERO, Duration.ofSeconds(-1))) {
            var operation = new Budgets(valid, valid, invalid);
            assertThrows(IllegalArgumentException.class, () -> new PartyMutationTimeouts(operation));
        }
    }

    /** Supplies immutable timeout settings without booting the configuration framework. */
    private record Budgets(Duration lockTimeout, Duration statementTimeout, Duration operationTimeout) implements PartyMutationConfiguration {
    }
}
