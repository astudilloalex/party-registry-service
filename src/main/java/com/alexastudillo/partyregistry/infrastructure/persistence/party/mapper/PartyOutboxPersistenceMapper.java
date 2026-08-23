package com.alexastudillo.partyregistry.infrastructure.persistence.party.mapper;

import java.util.UUID;

import com.alexastudillo.partyregistry.application.party.command.PartyCreationEventIntent;
import com.alexastudillo.partyregistry.infrastructure.persistence.party.entity.PartyOutboxEventEntity;

/**
 * Maps an application event intent to its persistence representation.
 */
public final class PartyOutboxPersistenceMapper {

    public PartyOutboxEventEntity toEntity(
            PartyCreationEventIntent eventIntent,
            UUID aggregateId,
            long aggregateVersion) {
        throw new UnsupportedOperationException("Party outbox mapping is not implemented");
    }
}
