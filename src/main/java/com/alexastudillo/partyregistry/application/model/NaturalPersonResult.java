package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Transport-neutral application result describing one natural person.
 */
public record NaturalPersonResult(
        PartyId partyId,
        TenantId tenantId,
        PartyType type,
        String displayName,
        PartyRecordStatus recordStatus,
        PartyVersion version,
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

    public NaturalPersonResult {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(recordStatus, "recordStatus");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(givenNames, "givenNames");
        Objects.requireNonNull(familyNames, "familyNames");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
    }

    /**
     * Projects a natural-person aggregate into its application result form.
     *
     * @param naturalPerson aggregate to project
     * @return the application result representation
     */
    public static NaturalPersonResult fromAggregate(NaturalPerson naturalPerson) {
        Objects.requireNonNull(naturalPerson, "naturalPerson");
        return new NaturalPersonResult(
                naturalPerson.partyId(),
                naturalPerson.tenantId(),
                naturalPerson.type(),
                naturalPerson.displayName(),
                naturalPerson.recordStatus(),
                naturalPerson.version(),
                naturalPerson.details().givenNames(),
                naturalPerson.details().familyNames(),
                naturalPerson.details().preferredName(),
                naturalPerson.details().birthDate(),
                naturalPerson.details().dateOfDeath(),
                naturalPerson.details().birthCountryCode(),
                naturalPerson.auditInfo().createdAt(),
                naturalPerson.auditInfo().createdBy(),
                naturalPerson.auditInfo().updatedAt(),
                naturalPerson.auditInfo().updatedBy());
    }
}
