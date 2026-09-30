package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.vertx.pgclient.PgException;
import org.hibernate.exception.ConstraintViolationException;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognizes only the two named nationality exclusion constraints after a PostgreSQL exclusion failure. */
final class NationalityExclusionFailure {

    private static final String EXCLUSION_STATE = "23P01";
    private static final String COUNTRY = "ex_party_nationalities_country_validity";
    private static final String PRIMARY = "ex_party_nationalities_primary_validity";
    private static final Pattern PG_EXCLUSION_LINE = Pattern.compile(
            "ERROR: conflicting key value violates exclusion constraint \"(ex_party_nationalities_country_validity|ex_party_nationalities_primary_validity)\" \\(23P01\\)");

    private NationalityExclusionFailure() {
    }

    /** Checks a structured name first; uses the exact PostgreSQL first line only when Hibernate omits that name. */
    static boolean isNamed(Throwable failure, String expected) {
        validateExpected(expected);
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (isExclusionViolation(current)) {
                return expected.equals(extractConstraint(current));
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private static void validateExpected(String expected) {
        if (!COUNTRY.equals(expected) && !PRIMARY.equals(expected)) {
            throw new IllegalArgumentException("Unknown nationality exclusion constraint");
        }
    }

    private static boolean isExclusionViolation(Throwable current) {
        if (current instanceof PgException postgres) {
            return EXCLUSION_STATE.equals(postgres.getSqlState());
        }
        if (current instanceof ConstraintViolationException hibernate) {
            SQLException sql = hibernate.getSQLException();
            return sql != null && EXCLUSION_STATE.equals(sql.getSQLState());
        }
        return false;
    }

    private static String extractConstraint(Throwable current) {
        if (current instanceof PgException postgres) {
            return postgres.getConstraint();
        }
        if (current instanceof ConstraintViolationException hibernate) {
            return extractHibernateConstraint(hibernate);
        }
        return null;
    }

    private static String extractHibernateConstraint(ConstraintViolationException hibernate) {
        if (hibernate.getConstraintName() != null) {
            return hibernate.getConstraintName();
        }
        SQLException sql = hibernate.getSQLException();
        if (sql == null || sql.getMessage() == null) {
            return null;
        }
        Matcher named = PG_EXCLUSION_LINE.matcher(sql.getMessage().lines().findFirst().orElse(""));
        return named.matches() ? named.group(1) : null;
    }
}
