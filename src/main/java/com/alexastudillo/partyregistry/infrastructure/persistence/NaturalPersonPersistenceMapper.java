package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;

/**
 * Maps natural-person aggregates to and from their Hibernate persistence model.
 */
@ApplicationScoped
public class NaturalPersonPersistenceMapper {

    PartyEntity toEntity(NaturalPerson naturalPerson) {
        Objects.requireNonNull(naturalPerson, "naturalPerson");
        AuditInfo audit = naturalPerson.auditInfo();
        PartyEntity party = new PartyEntity(
                naturalPerson.partyId().value(),
                naturalPerson.tenantId().value(),
                PartyType.NATURAL_PERSON,
                naturalPerson.displayName(),
                naturalPerson.recordStatus(),
                audit,
                naturalPerson.version().value());
        NaturalPersonDetails details = naturalPerson.details();
        party.attachNaturalPersonDetails(new NaturalPersonDetailsEntity(
                naturalPerson.partyId().value(),
                details,
                audit));
        return party;
    }

    NaturalPerson toDomain(PartyEntity party) {
        Objects.requireNonNull(party, "party");
        if (party.type() != PartyType.NATURAL_PERSON || party.naturalPersonDetails() == null) {
            throw new IllegalStateException("Persistence row is not a complete natural person");
        }
        NaturalPersonDetailsEntity details = party.naturalPersonDetails();
        return NaturalPerson.restore(
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
                new NaturalPersonDetails(
                        details.givenNames(),
                        details.familyNames(),
                        details.preferredName(),
                        details.birthDate(),
                        details.dateOfDeath(),
                        details.birthCountryCode()));
    }

}
