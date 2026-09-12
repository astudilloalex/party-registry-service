package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Defines safe, versioned event data eligible for atomic outbox storage.
 */
public sealed interface OutboxEventCandidate
        permits PartyCreatedOutboxCandidate,
        PartyIdentifierCreatedOutboxCandidate,
        PartyActivatedOutboxCandidate {

    UUID eventId();

    TenantId tenantId();

    String eventType();

    short eventSchemaVersion();

    Instant occurredAt();

    UUID correlationId();

    String createdBy();
}
