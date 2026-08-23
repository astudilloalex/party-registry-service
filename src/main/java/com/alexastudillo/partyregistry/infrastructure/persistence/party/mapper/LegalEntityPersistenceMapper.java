package com.alexastudillo.partyregistry.infrastructure.persistence.party.mapper;

import com.alexastudillo.partyregistry.domain.party.model.Party;
import com.alexastudillo.partyregistry.infrastructure.persistence.party.entity.LegalEntityDetailsEntity;

/**
 * Maps legal-entity aggregate data to persistence entities.
 */
public final class LegalEntityPersistenceMapper {

    public LegalEntityDetailsEntity toEntity(Party party) {
        throw new UnsupportedOperationException("Legal-entity mapping is not implemented");
    }
}
