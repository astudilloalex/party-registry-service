package com.alexastudillo.partyregistry.adapter.out.postgresql;

import com.alexastudillo.partyregistry.application.ApplicationFailure;
import io.vertx.pgclient.PgException;
import java.util.Map;

final class PostgreSqlErrorMapper {
    private static final Map<String, String> CONSTRAINT_CODES = Map.ofEntries(
            Map.entry("ct_parties_detail_type", "VALIDATION_ERROR"),
            Map.entry("ck_natural_person_life_dates", "VALIDATION_ERROR"),
            Map.entry("ck_natural_person_birth_country_code", "VALIDATION_ERROR"),
            Map.entry("ck_legal_entity_lifecycle_dates", "VALIDATION_ERROR"),
            Map.entry("ck_legal_entity_incorporation_country_code", "VALIDATION_ERROR"),
            Map.entry("ck_party_nationality_country_code", "VALIDATION_ERROR"),
            Map.entry("ck_party_nationality_validity", "VALIDATION_ERROR"),
            Map.entry("uq_party_nationalities_active_country", "CONFLICT"),
            Map.entry("uq_party_nationalities_active_primary", "CONFLICT"),
            Map.entry("fk_party_identifiers_party", "NOT_FOUND"),
            Map.entry("fk_party_identifiers_scheme", "NOT_FOUND"),
            Map.entry("uq_party_identifier_tenant_scheme_hash", "CONFLICT"),
            Map.entry("uq_party_identifiers_verified_primary_scheme", "CONFLICT"),
            Map.entry("ck_party_identifier_validity_dates", "VALIDATION_ERROR"),
            Map.entry("ck_party_identifier_verification", "VALIDATION_ERROR"),
            Map.entry("ck_party_identifier_expired_date", "VALIDATION_ERROR"),
            Map.entry("ck_party_identifier_hash_upper_hex", "VALIDATION_ERROR"),
            Map.entry("ck_party_identifier_normalization_version", "VALIDATION_ERROR"),
            Map.entry("ck_party_identifier_encryption_key_version", "VALIDATION_ERROR"),
            Map.entry("ck_party_identifier_nonnegative_version", "VALIDATION_ERROR"),
            Map.entry("ck_parties_nonnegative_version", "VALIDATION_ERROR"),
            Map.entry("ck_identifier_scheme_minimum_length", "VALIDATION_ERROR"),
            Map.entry("ck_identifier_scheme_maximum_length", "VALIDATION_ERROR"),
            Map.entry("ck_identifier_scheme_length_range", "VALIDATION_ERROR"),
            Map.entry("ck_identifier_schemes_nonnegative_version", "VALIDATION_ERROR"),
            Map.entry("ck_party_outbox_nonnegative_aggregate_version", "VALIDATION_ERROR"),
            Map.entry("ck_party_outbox_positive_schema_version", "VALIDATION_ERROR"),
            Map.entry("ck_party_outbox_nonnegative_attempts", "VALIDATION_ERROR"),
            Map.entry("ck_party_outbox_nonnegative_version", "VALIDATION_ERROR"),
            Map.entry("ck_party_outbox_published_at", "VALIDATION_ERROR"),
            Map.entry("ck_party_outbox_failed_error", "VALIDATION_ERROR"));

    private PostgreSqlErrorMapper() {}

    static Throwable map(Throwable failure) {
        if (failure instanceof ApplicationFailure) {
            return failure;
        }
        PgException postgres = findPgException(failure);
        if (postgres == null) {
            return new ApplicationFailure("INTERNAL_ERROR", "The persistence operation failed");
        }
        String code = CONSTRAINT_CODES.get(postgres.getConstraint());
        if (code == null) {
            code = switch (postgres.getSqlState()) {
                case "23505" -> "CONFLICT";
                case "23503" -> "NOT_FOUND";
                case "23000", "23502", "23514" -> "VALIDATION_ERROR";
                default -> "INTERNAL_ERROR";
            };
        }
        return new ApplicationFailure(code, safeMessage(code));
    }

    private static PgException findPgException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof PgException postgres) {
                return postgres;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String safeMessage(String code) {
        return switch (code) {
            case "CONFLICT" -> "The requested persistence change conflicts with existing data";
            case "NOT_FOUND" -> "A required resource was not found";
            case "VALIDATION_ERROR" -> "The requested persistence change violates a data rule";
            default -> "The persistence operation failed";
        };
    }
}
