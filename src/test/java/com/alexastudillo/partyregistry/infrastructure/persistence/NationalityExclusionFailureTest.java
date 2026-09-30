package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.vertx.pgclient.PgException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the exact SQLSTATE/name fallback without classifying unrelated
 * PostgreSQL failures.
 */
class NationalityExclusionFailureTest {

    private static final String COUNTRY = "ex_party_nationalities_country_validity";
    private static final String PRIMARY = "ex_party_nationalities_primary_validity";
    private static final String LINE = "ERROR: conflicting key value violates exclusion constraint \"%s\" (23P01)";

    @Test
    void recognizesExactNameFromHibernateWhenStructuredNameIsAbsent() {
        var country = wrapped("23P01", LINE.formatted(COUNTRY), null);
        var primary = wrapped("23P01", LINE.formatted(PRIMARY), null);
        assertTrue(NationalityExclusionFailure.isNamed(country, COUNTRY));
        assertFalse(NationalityExclusionFailure.isNamed(country, PRIMARY));
        assertTrue(NationalityExclusionFailure.isNamed(primary, PRIMARY));
        assertFalse(NationalityExclusionFailure.isNamed(primary, COUNTRY));
    }

    @Test
    void recognizesPgExceptionWhenExclusionStateMatches() {
        PgException country = postgresFailure("23P01", COUNTRY);
        PgException primary = postgresFailure("23P01", PRIMARY);
        PgException wrongState = postgresFailure("23505", COUNTRY);
        assertTrue(NationalityExclusionFailure.isNamed(country, COUNTRY));
        assertFalse(NationalityExclusionFailure.isNamed(country, PRIMARY));
        assertTrue(NationalityExclusionFailure.isNamed(primary, PRIMARY));
        assertFalse(NationalityExclusionFailure.isNamed(wrongState, COUNTRY));
    }

    @Test
    void structuredNameWinsAndWrongStatesOrMessageShapesRemainUnexpected() {
        assertTrue(NationalityExclusionFailure.isNamed(wrapped("23P01", "unrelated", COUNTRY), COUNTRY));
        assertFalse(NationalityExclusionFailure.isNamed(wrapped("23P01", LINE.formatted(COUNTRY), PRIMARY), COUNTRY));
        assertFalse(NationalityExclusionFailure.isNamed(wrapped("23505", LINE.formatted(COUNTRY), null), COUNTRY));
        assertFalse(NationalityExclusionFailure.isNamed(wrapped("23P01", LINE.formatted("another_constraint"), null),
                COUNTRY));
        assertFalse(NationalityExclusionFailure.isNamed(wrapped("23P01", "prefix " + LINE.formatted(COUNTRY), null),
                COUNTRY));
        assertFalse(NationalityExclusionFailure
                .isNamed(wrapped("23P01", "other violation \"" + COUNTRY + "\" (23P01)", null), COUNTRY));
        assertFalse(NationalityExclusionFailure.isNamed(wrapped("23P01", LINE.formatted(COUNTRY) + " extra", null),
                COUNTRY));
        assertFalse(NationalityExclusionFailure.isNamed(wrapped("23P01", null, null), COUNTRY));
        assertFalse(NationalityExclusionFailure.isNamed(new SQLException(LINE.formatted(COUNTRY), "23P01"), COUNTRY));
        assertFalse(NationalityExclusionFailure.isNamed(null, COUNTRY));
    }

    @Test
    void rejectsUnknownExclusionConstraint() {
        var failure = new RuntimeException();
        assertThrows(IllegalArgumentException.class,
                () -> NationalityExclusionFailure.isNamed(failure, "unknown_constraint"));
    }

    private static Throwable wrapped(String state, String message, String constraint) {
        return new CompletionException(new ConstraintViolationException("Hibernate constraint violation",
                new SQLException(message, state), constraint));
    }

    private static PgException postgresFailure(String sqlState, String constraint) {
        return new PgException(
                "exclusion failure",
                "ERROR",
                sqlState,
                "detail",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "public",
                "party_nationalities",
                null,
                null,
                constraint);
    }
}
