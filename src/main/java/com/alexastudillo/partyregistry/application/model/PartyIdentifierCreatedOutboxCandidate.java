package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Carries the minimal safe data for a version-one identifier-created event.
 */
public record PartyIdentifierCreatedOutboxCandidate(
        UUID eventId,
        TenantId tenantId,
        PartyIdentifierId identifierId,
        PartyIdentifierVersion identifierVersion,
        PartyId partyId,
        String schemeCode,
        PartyIdentifierStatus status,
        Instant occurredAt,
        UUID correlationId,
        String createdBy) implements OutboxEventCandidate {

    /** Versioned integration-event name. */
    public static final String EVENT_TYPE = "party.identifier-created.v1";

    public PartyIdentifierCreatedOutboxCandidate {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(identifierId, "identifierId");
        Objects.requireNonNull(identifierVersion, "identifierVersion");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(correlationId, "correlationId");
        if (schemeCode == null || schemeCode.isBlank()) {
            throw new IllegalArgumentException("Identifier scheme code is required");
        }
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
