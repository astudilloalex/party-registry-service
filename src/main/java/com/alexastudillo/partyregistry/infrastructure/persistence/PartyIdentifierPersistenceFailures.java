package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import io.smallrye.mutiny.TimeoutException;
import io.vertx.pgclient.PgException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Translates genuine identifier-store outages and deadlines while preserving business and unexpected failures. */
final class PartyIdentifierPersistenceFailures {
    private PartyIdentifierPersistenceFailures() { }

    static Throwable translate(Throwable failure) {
        if (!PersistenceExceptionTranslator.requiresTranslation(failure)) { return failure; }
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            String state = switch (current) {
                case PgException postgres -> postgres.getSqlState();
                case SQLException sql -> sql.getSQLState();
                default -> null;
            };
            if (current instanceof TimeoutException || current instanceof ConnectException || current instanceof NoRouteToHostException
                    || state != null && (state.startsWith("08") || Set.of("55P03", "57014", "57P01", "57P02", "57P03").contains(state))) {
                return new ApplicationException(new ApplicationFailure.DependencyUnavailable("party-identifier-store"), failure);
            }
        }
        return failure;
    }
}
