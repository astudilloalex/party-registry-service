package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Carries only approved identifier mutation metadata and safe resulting state, never value, reason or replay material. */
public record PartyIdentifierChangedOutboxCandidate(UUID eventId, TenantId tenantId, PartyIdentifierId identifierId,
        PartyIdentifierVersion identifierVersion, PartyId partyId, String schemeCode, PartyIdentifierStatus status, boolean isPrimary,
        Kind kind, Instant occurredAt, UUID correlationId, String createdBy) implements OutboxEventCandidate {

    public static final String UPDATED_EVENT_TYPE = "party.identifier-updated.v1";
    public static final String VERIFIED_EVENT_TYPE = "party.identifier-verified.v1";
    public static final String REJECTED_EVENT_TYPE = "party.identifier-rejected.v1";
    public static final String REVOKED_EVENT_TYPE = "party.identifier-revoked.v1";
    public static final String PRIMARY_CHANGED_EVENT_TYPE = "party.identifier-primary-changed.v1";

    public PartyIdentifierChangedOutboxCandidate {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(identifierId, "identifierId");
        Objects.requireNonNull(identifierVersion, "identifierVersion");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(correlationId, "correlationId");
        if (identifierVersion.value() == 0 || !kind.accepts(status, isPrimary)) {
            throw new IllegalArgumentException("Identifier event version or resulting state is inconsistent");
        }
        if (schemeCode == null || schemeCode.isBlank() || schemeCode.codePointCount(0, schemeCode.length()) > 64) {
            throw new IllegalArgumentException("Identifier event scheme code is invalid");
        }
        if (createdBy == null || createdBy.isBlank() || createdBy.codePointCount(0, createdBy.length()) > 128
                || createdBy.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Identifier event audit actor is invalid");
        }
    }

    @Override
    public String eventType() { return kind.eventType(); }

    @Override
    public short eventSchemaVersion() { return 1; }

    /** Defines accepted event names and payload coherence without choosing business transition eligibility. */
    public enum Kind {
        UPDATED, VERIFIED, REJECTED, REVOKED, PRIMARY_CHANGED;

        public String eventType() {
            return switch (this) {
                case UPDATED -> UPDATED_EVENT_TYPE;
                case VERIFIED -> VERIFIED_EVENT_TYPE;
                case REJECTED -> REJECTED_EVENT_TYPE;
                case REVOKED -> REVOKED_EVENT_TYPE;
                case PRIMARY_CHANGED -> PRIMARY_CHANGED_EVENT_TYPE;
            };
        }

        /** Allows both sides of a primary replacement and retained flags on metadata or verification events. */
        public boolean accepts(PartyIdentifierStatus status, boolean primary) {
            return switch (this) {
                case UPDATED -> status == PartyIdentifierStatus.PENDING_VERIFICATION || status == PartyIdentifierStatus.VERIFIED;
                case VERIFIED, PRIMARY_CHANGED -> status == PartyIdentifierStatus.VERIFIED;
                case REJECTED -> status == PartyIdentifierStatus.REJECTED && !primary;
                case REVOKED -> status == PartyIdentifierStatus.REVOKED && !primary;
            };
        }

        /** Resolves only declared version-one names; unknown kinds never degrade into another event. */
        public static Kind fromEventType(String type) {
            return switch (type) {
                case UPDATED_EVENT_TYPE -> UPDATED;
                case VERIFIED_EVENT_TYPE -> VERIFIED;
                case REJECTED_EVENT_TYPE -> REJECTED;
                case REVOKED_EVENT_TYPE -> REVOKED;
                case PRIMARY_CHANGED_EVENT_TYPE -> PRIMARY_CHANGED;
                default -> throw new IllegalArgumentException("Unsupported identifier event type");
            };
        }
    }
}
