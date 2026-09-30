package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.quarkus.runtime.Startup;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.Duration;
import java.util.Objects;

/** Validates and applies bounded root mutation waits without leaking settings into pooled connections. */
@Startup
@Singleton
public final class PartyMutationTimeouts {

    private final long lockMillis;
    private final long statementMillis;
    private final Duration operation;

    /** Rejects disabled or PostgreSQL-unrepresentable timeouts at startup. */
    @Inject
    public PartyMutationTimeouts(PartyMutationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        lockMillis = milliseconds(configuration.lockTimeout());
        statementMillis = milliseconds(configuration.statementTimeout());
        operation = configuration.operationTimeout();
        if (operation.isZero() || operation.isNegative()) {
            throw new IllegalArgumentException("Party mutation work timeout must be positive");
        }
    }

    Uni<Void> configure(Mutiny.Session session) {
        return session.createNativeQuery("SET TRANSACTION ISOLATION LEVEL READ COMMITTED, READ WRITE").executeUpdate()
                .chain(() -> session.createNativeQuery("""
                        select set_config('lock_timeout', :lock, true), set_config('statement_timeout', :statement, true)
                        """, Object[].class).setParameter("lock", Long.toString(lockMillis))
                        .setParameter("statement", Long.toString(statementMillis)).getSingleResult()).replaceWithVoid();
    }

    <T> Uni<T> limit(Uni<T> work) {
        return work.ifNoItem().after(operation).fail();
    }

    private static long milliseconds(Duration value) {
        long result = value.toMillis();
        if (result < 1 || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Party mutation SQL timeout must be positive and supported by PostgreSQL");
        }
        return result;
    }
}
