package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.LegalEntity;
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
 * Transport-neutral application result describing one legal entity.
 */
public record LegalEntityResult(
        PartyId partyId,
        TenantId tenantId,
        PartyType type,
        String displayName,
        PartyRecordStatus recordStatus,
        PartyVersion version,
        String legalName,
        @Nullable String tradeName,
        @Nullable String legalFormCode,
        String incorporationCountryCode,
        @Nullable LocalDate incorporatedOn,
        @Nullable LocalDate dissolvedOn,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) implements PartyDetailsResult {

    public LegalEntityResult {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(recordStatus, "recordStatus");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(legalName, "legalName");
        Objects.requireNonNull(incorporationCountryCode, "incorporationCountryCode");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
        if (type != PartyType.LEGAL_ENTITY) {
            throw new IllegalArgumentException("Legal-entity result requires LEGAL_ENTITY type");
        }
    }

    /**
     * Projects a legal-entity aggregate into its application result form.
     *
     * @param legalEntity aggregate to project
     * @return the application result representation
     */
    public static LegalEntityResult fromAggregate(LegalEntity legalEntity) {
        Objects.requireNonNull(legalEntity, "legalEntity");
        return new LegalEntityResult(
                legalEntity.partyId(),
                legalEntity.tenantId(),
                legalEntity.type(),
                legalEntity.displayName(),
                legalEntity.recordStatus(),
                legalEntity.version(),
                legalEntity.details().legalName(),
                legalEntity.details().tradeName(),
                legalEntity.details().legalFormCode(),
                legalEntity.details().incorporationCountryCode(),
                legalEntity.details().incorporatedOn(),
                legalEntity.details().dissolvedOn(),
                legalEntity.auditInfo().createdAt(),
                legalEntity.auditInfo().createdBy(),
                legalEntity.auditInfo().updatedAt(),
                legalEntity.auditInfo().updatedBy());
    }
}
