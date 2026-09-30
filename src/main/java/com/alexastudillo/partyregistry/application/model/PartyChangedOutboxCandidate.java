package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Carries only safe root metadata for accepted label corrections, deactivations, and archival events. */
public record PartyChangedOutboxCandidate(
        UUID eventId, TenantId tenantId, PartyId partyId, PartyVersion partyVersion, PartyType partyType,
        PartyRecordStatus status, Kind kind, Instant occurredAt, UUID correlationId, String createdBy) implements OutboxEventCandidate {

    public static final String UPDATED_EVENT_TYPE = "party.updated.v1";
    public static final String DEACTIVATED_EVENT_TYPE = "party.deactivated.v1";
    public static final String ARCHIVED_EVENT_TYPE = "party.archived.v1";

    public PartyChangedOutboxCandidate {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(partyVersion, "partyVersion");
        Objects.requireNonNull(partyType, "partyType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(correlationId, "correlationId");
        if (partyVersion.value() <= 0 || !kind.accepts(status)) {
            throw new IllegalArgumentException("Root event must describe a valid accepted version and status");
        }
        if (createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("Outbox audit user is required");
        }
    }

    @Override
    public String eventType() {
        return kind.eventType();
    }

    @Override
    public short eventSchemaVersion() {
        return 1;
    }

    /** Defines the closed set of new root event names and their resulting-state contracts. */
    public enum Kind {
        UPDATED, DEACTIVATED, ARCHIVED;

        public String eventType() {
            return switch (this) {
                case UPDATED -> UPDATED_EVENT_TYPE;
                case DEACTIVATED -> DEACTIVATED_EVENT_TYPE;
                case ARCHIVED -> ARCHIVED_EVENT_TYPE;
            };
        }

        /** Checks an event's resulting state, without deciding whether a transition is eligible. */
        public boolean accepts(PartyRecordStatus status) {
            Objects.requireNonNull(status, "status");
            return switch (this) {
                case UPDATED -> true;
                case DEACTIVATED -> status == PartyRecordStatus.INACTIVE;
                case ARCHIVED -> status == PartyRecordStatus.ARCHIVED;
            };
        }

        /** Resolves only supported version-one names; unknown names fail rather than degrading to a generic event. */
        public static Kind fromEventType(String eventType) {
            return switch (eventType) {
                case UPDATED_EVENT_TYPE -> UPDATED;
                case DEACTIVATED_EVENT_TYPE -> DEACTIVATED;
                case ARCHIVED_EVENT_TYPE -> ARCHIVED;
                default -> throw new IllegalArgumentException("Unsupported root event type");
            };
        }
    }
}
