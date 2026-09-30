package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Defines version one of the immutable application-result snapshot persisted
 * for replay.
 */
@RegisterForReflection
public record NaturalPersonResultSnapshot(
        short schemaVersion,
        UUID partyId,
        UUID tenantId,
        String type,
        String displayName,
        String recordStatus,
        long version,
        String givenNames,
        String familyNames,
        @Nullable String preferredName,
        @Nullable LocalDate birthDate,
        @Nullable LocalDate dateOfDeath,
        @Nullable String birthCountryCode,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    static final short CURRENT_SCHEMA_VERSION = 1;

    static NaturalPersonResultSnapshot from(NaturalPersonResult result) {
        return new NaturalPersonResultSnapshot(
                CURRENT_SCHEMA_VERSION,
                result.partyId().value(),
                result.tenantId().value(),
                result.type().name(),
                result.displayName(),
                result.recordStatus().name(),
                result.version().value(),
                result.givenNames(),
                result.familyNames(),
                result.preferredName(),
                result.birthDate(),
                result.dateOfDeath(),
                result.birthCountryCode(),
                result.createdAt(),
                result.createdBy(),
                result.updatedAt(),
                result.updatedBy());
    }

    NaturalPersonResult toResult() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported idempotency snapshot schema version");
        }
        PartyType partyType = PartyType.valueOf(type);
        if (partyType != PartyType.NATURAL_PERSON) {
            throw new IllegalStateException("Idempotency snapshot has an unsupported party type");
        }
        return new NaturalPersonResult(
                new PartyId(partyId),
                new TenantId(tenantId),
                partyType,
                displayName,
                PartyRecordStatus.valueOf(recordStatus),
                new PartyVersion(version),
                givenNames,
                familyNames,
                preferredName,
                birthDate,
                dateOfDeath,
                birthCountryCode,
                createdAt,
                createdBy,
                updatedAt,
                updatedBy);
    }
}
