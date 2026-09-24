package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies finite query budget validation and bounded failure of a stalled traversal.
 */
class PartyQueryTimeoutsTest {

    @Test
    void rejectsDisabledNegativeAndUnsupportedStatementBudgets() {
        assertInvalid(Duration.ZERO, Duration.ofSeconds(1));
        assertInvalid(Duration.ofMillis(-1), Duration.ofSeconds(1));
        assertInvalid(Duration.ofNanos(1), Duration.ofSeconds(1));
        assertInvalid(Duration.ofMillis((long) Integer.MAX_VALUE + 1), Duration.ofSeconds(1));
        assertInvalid(Duration.ofSeconds(1), Duration.ZERO);
        assertInvalid(Duration.ofSeconds(1), Duration.ofSeconds(-1));
    }

    @Test
    void stalledTraversalFailsWithinItsConfiguredBudget() {
        var policy = new PartyQueryTimeouts(new TestConfiguration(Duration.ofSeconds(1), Duration.ofMillis(50)));
        policy.limit(Uni.createFrom().nothing()).subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure(Duration.ofSeconds(2)).assertFailedWith(io.smallrye.mutiny.TimeoutException.class);
    }

    private static void assertInvalid(Duration statement, Duration traversal) {
        var configuration = new TestConfiguration(statement, traversal);
        assertThrows(IllegalArgumentException.class, () -> new PartyQueryTimeouts(configuration));
    }

    /** Supplies explicit timeout values without changing the application's runtime configuration. */
    private record TestConfiguration(Duration statementTimeout, Duration traversalTimeout) implements PartyQueryConfiguration {
    }
}
