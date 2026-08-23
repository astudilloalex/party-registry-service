package com.alexastudillo.partyregistry.infrastructure.persistence.party.mapper;

import java.util.List;

import com.alexastudillo.partyregistry.domain.party.model.Party;
import com.alexastudillo.partyregistry.infrastructure.persistence.party.entity.NaturalPersonDetailsEntity;
import com.alexastudillo.partyregistry.infrastructure.persistence.party.entity.PartyNationalityEntity;

/**
 * Maps natural-person aggregate data to persistence entities.
 */
public final class NaturalPersonPersistenceMapper {

    public NaturalPersonDetailsEntity toDetailsEntity(Party party) {
        throw new UnsupportedOperationException("Natural-person mapping is not implemented");
    }

    public List<PartyNationalityEntity> toNationalityEntities(Party party) {
        throw new UnsupportedOperationException("Nationality mapping is not implemented");
    }
}
