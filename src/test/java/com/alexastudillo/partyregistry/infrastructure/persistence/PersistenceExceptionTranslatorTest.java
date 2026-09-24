package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import io.vertx.pgclient.PgException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies sanitized persistence-failure translation and cancellation handling.
 */
class PersistenceExceptionTranslatorTest {

    @Test
    void preservesCancellationAndTypedApplicationFailures() {
        ApplicationException applicationFailure = new ApplicationException(
                new ApplicationFailure.PersistenceFailure());
        assertFalse(PersistenceExceptionTranslator.requiresTranslation(new CancellationException()));
        assertFalse(PersistenceExceptionTranslator.requiresTranslation(
                new CompletionException(new CancellationException())));
        assertFalse(PersistenceExceptionTranslator.requiresTranslation(
                new CompletionException(new IllegalStateException(new CancellationException()))));
        assertFalse(PersistenceExceptionTranslator.requiresTranslation(applicationFailure));
        assertFalse(PersistenceExceptionTranslator.requiresTranslation(
                new CompletionException(applicationFailure)));
    }

    @Test
    void sanitizesUnexpectedInfrastructureFailures() {
        RuntimeException databaseFailure = new RuntimeException("sensitive SQL detail");

        ApplicationException translated = PersistenceExceptionTranslator.toApplicationException(databaseFailure);

        assertInstanceOf(ApplicationFailure.PersistenceFailure.class, translated.failure());
        assertSame(databaseFailure, translated.getCause());
        assertFalse(translated.getMessage().contains("sensitive SQL detail"));
    }

    @Test
    void classifiesOnlyTheExactSqlStateAndConstraintPair() {
        PgException idempotencyConflict = postgresFailure(
                "23505",
                "pk_api_idempotency_records");
        PgException identifierConflict = postgresFailure(
                "23505",
                "uq_party_identifiers_active_value");

        assertTrue(PersistenceExceptionTranslator.isConstraint(
                new CompletionException(idempotencyConflict),
                "23505",
                "pk_api_idempotency_records"));
        assertTrue(PersistenceExceptionTranslator.isConstraint(
                identifierConflict,
                "23505",
                "uq_party_identifiers_active_value"));
        assertFalse(PersistenceExceptionTranslator.isConstraint(
                identifierConflict,
                "23505",
                "uq_party_identifiers_verified_primary"));
        assertFalse(PersistenceExceptionTranslator.isConstraint(
                postgresFailure("23514", "uq_party_identifiers_active_value"),
                "23505",
                "uq_party_identifiers_active_value"));
    }

    private static PgException postgresFailure(String sqlState, String constraint) {
        return new PgException(
                "sensitive database failure",
                "ERROR",
                sqlState,
                "sensitive detail",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "public",
                "party_identifiers",
                null,
                null,
                constraint);
    }
}
