package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;

/**
 * Maps legal-entity aggregates to and from their Hibernate persistence model.
 */
@ApplicationScoped
public class LegalEntityPersistenceMapper {

    PartyEntity toEntity(LegalEntity legalEntity) {
        Objects.requireNonNull(legalEntity, "legalEntity");
        AuditInfo audit = legalEntity.auditInfo();
        PartyEntity party = new PartyEntity(
                legalEntity.partyId().value(),
                legalEntity.tenantId().value(),
                PartyType.LEGAL_ENTITY,
                legalEntity.displayName(),
                legalEntity.recordStatus(),
                audit,
                legalEntity.version().value());
        party.attachLegalEntityDetails(new LegalEntityDetailsEntity(
                legalEntity.partyId().value(),
                legalEntity.details(),
                audit));
        return party;
    }

    LegalEntity toDomain(PartyEntity party) {
        Objects.requireNonNull(party, "party");
        if (party.type() != PartyType.LEGAL_ENTITY
                || party.legalEntityDetails() == null
                || party.naturalPersonDetails() != null) {
            throw new IllegalStateException("Persistence row is not a complete legal entity");
        }
        return toDomain(party, party.legalEntityDetails());
    }

    LegalEntity toDomain(PartyEntity party, LegalEntityDetailsEntity details) {
        Objects.requireNonNull(party, "party");
        if (party.type() != PartyType.LEGAL_ENTITY || details == null) {
            throw new IllegalStateException("Persistence row is not a complete legal entity");
        }
        return LegalEntity.restore(
                new PartyId(party.id()),
                new TenantId(party.tenantId()),
                party.displayName(),
                party.recordStatus(),
                new PartyVersion(party.version()),
                new AuditInfo(
                        party.createdAt(),
                        party.createdBy(),
                        party.updatedAt(),
                        party.updatedBy()),
                new LegalEntityDetails(
                        details.legalName(),
                        details.tradeName(),
                        details.legalFormCode(),
                        details.incorporationCountryCode(),
                        details.incorporatedOn(),
                        details.dissolvedOn()));
    }
}
