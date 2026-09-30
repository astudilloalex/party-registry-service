package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Carries the minimal safe data for a version-one Party-created outbox event.
 */
public record PartyCreatedOutboxCandidate(
        UUID eventId,
        TenantId tenantId,
        PartyId partyId,
        PartyVersion partyVersion,
        PartyType partyType,
        Instant occurredAt,
        UUID correlationId,
        String createdBy) implements OutboxEventCandidate {

    /** Versioned integration-event name. */
    public static final String EVENT_TYPE = "party.created.v1";

    public PartyCreatedOutboxCandidate {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(partyVersion, "partyVersion");
        Objects.requireNonNull(partyType, "partyType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(correlationId, "correlationId");
        if (createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("Outbox audit user is required");
        }
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public short eventSchemaVersion() {
        return 1;
    }
}
