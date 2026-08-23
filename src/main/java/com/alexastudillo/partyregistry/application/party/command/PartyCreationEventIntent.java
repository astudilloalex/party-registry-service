package com.alexastudillo.partyregistry.application.party.command;

import com.alexastudillo.partyregistry.domain.party.model.PartyType;
import java.time.Instant;
import java.util.UUID;

public record PartyCreationEventIntent(
        UUID tenantId,
        String eventType,
        int eventSchemaVersion,
        PartyType partyType,
        Instant occurredAt,
        String userId) {
}
