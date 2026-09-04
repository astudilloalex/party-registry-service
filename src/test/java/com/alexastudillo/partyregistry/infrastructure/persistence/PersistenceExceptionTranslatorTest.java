package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies sanitized persistence-failure translation and cancellation handling.
 */
class PersistenceExceptionTranslatorTest {

    @Test
    void preservesCancellationAndTypedApplicationFailures() {
        assertFalse(PersistenceExceptionTranslator.requiresTranslation(new CancellationException()));
        assertFalse(PersistenceExceptionTranslator.requiresTranslation(
                new CompletionException(new CancellationException())));
        assertFalse(PersistenceExceptionTranslator.requiresTranslation(
                new ApplicationException(new ApplicationFailure.PersistenceFailure())));
    }

    @Test
    void sanitizesUnexpectedInfrastructureFailures() {
        RuntimeException databaseFailure = new RuntimeException("sensitive SQL detail");

        ApplicationException translated = PersistenceExceptionTranslator.toApplicationException(databaseFailure);

        assertInstanceOf(ApplicationFailure.PersistenceFailure.class, translated.failure());
        assertSame(databaseFailure, translated.getCause());
        assertFalse(translated.getMessage().contains("sensitive SQL detail"));
    }
}
