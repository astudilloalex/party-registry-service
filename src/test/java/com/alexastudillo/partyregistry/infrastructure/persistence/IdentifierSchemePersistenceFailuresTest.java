package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import io.smallrye.mutiny.TimeoutException;
import io.vertx.pgclient.PgException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/** Pins scheme-only failure classification without broadening Party or unknown failure semantics. */
class IdentifierSchemePersistenceFailuresTest {
    @Test
    void requiresBothExactUniqueConstraintAndSqlState() {
        assertFailure(pg("23505", "uq_identifier_schemes_code"), ApplicationFailure.IdentifierSchemeCodeConflict.class);
        assertFailure(new ConstraintViolationException("private", new SQLException("private", "23505"), "uq_identifier_schemes_code"),
                ApplicationFailure.IdentifierSchemeCodeConflict.class);
        for (Throwable failure : List.of(pg("23514", "uq_identifier_schemes_code"), pg("23505", "another_constraint"),
                new SQLException("uq_identifier_schemes_code", "23505"))) {
            assertSame(failure, IdentifierSchemePersistenceFailures.translate(failure));
        }
    }

    @Test
    void recognizesOnlyClassifiedDependencyOutagesAndDeadlines() {
        for (Throwable failure : List.of(pg("08006", null), pg("08001", null), pg("57P01", null), pg("57P02", null),
                pg("57P03", null), pg("55P03", null), pg("57014", null), new ConnectException("private"), new TimeoutException())) {
            assertFailure(new CompletionException(failure), ApplicationFailure.DependencyUnavailable.class);
        }
        for (Throwable failure : List.of(new IllegalStateException("private"), pg("23503", null), pg("42601", null),
                pg("40001", null), new java.util.concurrent.TimeoutException("unclassified"))) {
            assertSame(failure, IdentifierSchemePersistenceFailures.translate(failure));
        }
    }

    @Test
    void preservesWrappedCancellationAndApplicationFailuresBeforeInspectingCauses() {
        for (Throwable failure : List.of(new CancellationException(), new CompletionException(new CancellationException()),
                new ApplicationException(new ApplicationFailure.PersistenceFailure(), pg("57014", null)))) {
            assertSame(failure, IdentifierSchemePersistenceFailures.translate(failure));
        }
        var failure = pg("55P03", null);
        assertInstanceOf(ApplicationFailure.PersistenceFailure.class, PersistenceExceptionTranslator.toApplicationException(failure).failure());
    }

    private static void assertFailure(Throwable failure, Class<? extends ApplicationFailure> type) {
        var translated = assertInstanceOf(ApplicationException.class, IdentifierSchemePersistenceFailures.translate(failure));
        assertInstanceOf(type, translated.failure());
        assertSame(failure, translated.getCause());
    }

    private static PgException pg(String state, String constraint) {
        return new PgException("private", "ERROR", state, "private", null, null, null, null, null, null, null, null,
                "public", "identifier_schemes", null, null, constraint);
    }
}
