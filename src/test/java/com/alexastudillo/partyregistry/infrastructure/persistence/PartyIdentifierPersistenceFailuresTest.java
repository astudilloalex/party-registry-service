package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import io.smallrye.mutiny.TimeoutException;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Verifies narrow dependency classification while preserving business, cancellation and unexpected failure identity. */
class PartyIdentifierPersistenceFailuresTest {
    @Test
    void classifiesOnlyRealConnectionAndDeadlineFailuresAsDependencyUnavailable() {
        for (Throwable problem : List.of(new ConnectException("synthetic connection failure"), new TimeoutException(),
                new SQLException("synthetic lock failure", "55P03"), new SQLException("synthetic statement failure", "57014"),
                new SQLException("synthetic transport failure", "08006"))) {
            var failure = assertInstanceOf(ApplicationException.class, PartyIdentifierPersistenceFailures.translate(problem));
            var dependency = assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, failure.failure());
            assertEquals("party-identifier-store", dependency.dependencyName()); assertSame(problem, failure.getCause());
        }
    }

    @Test
    void preservesUnexpectedConstraintFailuresRatherThanFabricatingBusinessConflicts() {
        for (Throwable problem : List.of(new SQLException("synthetic foreign key failure", "23503"),
                new SQLException("synthetic unclassified unique failure", "23505"), new IllegalStateException("synthetic invariant"))) {
            assertSame(problem, PartyIdentifierPersistenceFailures.translate(problem));
        }
    }

    @Test
    void preservesCancellationAndKnownApplicationFailures() {
        for (Throwable problem : List.of(new CancellationException(), new ApplicationException(new ApplicationFailure.PrimaryIdentifierConflict()))) {
            assertSame(problem, PartyIdentifierPersistenceFailures.translate(problem));
        }
    }
}
