package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import io.vertx.pgclient.PgException;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleStateException;
import org.hibernate.exception.ConstraintViolationException;

import java.util.concurrent.CancellationException;

/**
 * Classifies reactive persistence failures without leaking database details.
 */
final class PersistenceExceptionTranslator {

    private PersistenceExceptionTranslator() {
    }

    static boolean isConstraint(Throwable failure, String sqlState, String constraintName) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof PgException postgresFailure
                    && sqlState.equals(postgresFailure.getSqlState())
                    && constraintName.equals(postgresFailure.getConstraint())) {
                return true;
            }
            if (current instanceof ConstraintViolationException constraintFailure
                    && constraintName.equals(constraintFailure.getConstraintName())
                    && sqlState.equals(constraintFailure.getSQLException().getSQLState())) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    static boolean isOptimisticLock(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof OptimisticLockException || current instanceof StaleStateException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    static boolean requiresTranslation(Throwable failure) {
        return !(failure instanceof ApplicationException)
                && !containsCancellation(failure);
    }

    static ApplicationException toApplicationException(Throwable failure) {
        return new ApplicationException(new ApplicationFailure.PersistenceFailure(), failure);
    }

    private static boolean containsCancellation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof CancellationException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }
}
