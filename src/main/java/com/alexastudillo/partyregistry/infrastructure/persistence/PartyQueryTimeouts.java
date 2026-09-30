package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.quarkus.runtime.Startup;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.Duration;
import java.util.Objects;

/**
 * Validates root-query deadlines and bounds server work so timeout or cancellation can complete transaction cleanup.
 */
@Startup
@Singleton
public final class PartyQueryTimeouts {

    private final long statementMillis;
    private final Duration traversal;

    /** Validates positive finite budgets; a zero PostgreSQL timeout would disable the required bound. */
    @Inject
    public PartyQueryTimeouts(PartyQueryConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        statementMillis = configuration.statementTimeout().toMillis();
        traversal = configuration.traversalTimeout();
        if (statementMillis < 1 || statementMillis > Integer.MAX_VALUE || traversal.isNegative() || traversal.isZero()) {
            throw new IllegalArgumentException("Party query timeouts must be positive and supported by PostgreSQL");
        }
    }

    /** Applies a transaction-local statement deadline without modifying pooled-connection defaults. */
    Uni<Void> configure(Mutiny.Session session) {
        return session.createNativeQuery("select set_config('statement_timeout', :timeout, true)", String.class)
                .setParameter("timeout", Long.toString(statementMillis)).getSingleResult().replaceWithVoid();
    }

    /** Fails inside the transaction so the framework completes rollback before propagating the failure. */
    <T> Uni<T> limit(Uni<T> work) {
        return work.ifNoItem().after(traversal).fail();
    }
}
