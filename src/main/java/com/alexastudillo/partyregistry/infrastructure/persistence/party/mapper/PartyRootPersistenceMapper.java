package com.alexastudillo.partyregistry.infrastructure.persistence.party.mapper;

import java.util.Objects;

import com.alexastudillo.partyregistry.domain.party.model.Party;
import com.alexastudillo.partyregistry.infrastructure.persistence.party.entity.PartyEntity;

/**
 * Maps the Party aggregate root to its persistence representation.
 */
public final class PartyRootPersistenceMapper {

    public PartyEntity toEntity(Party party) {
        Objects.requireNonNull(party, "party must not be null");
        return new PartyEntity(
                party.tenantId(),
                party.type(),
                party.displayName(),
                party.recordStatus(),
                party.createdAt(),
                party.createdBy(),
                party.updatedAt(),
                party.updatedBy(),
                party.version());
    }
}
