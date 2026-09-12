package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyActivatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Maps versioned safe application event candidates to and from outbox rows.
 */
@ApplicationScoped
public class PartyOutboxEventPersistenceMapper {

    private static final String PAYLOAD_KEY_PARTY_TYPE = "partyType";
    private static final String PAYLOAD_KEY_STATUS = "status";
    private static final String PAYLOAD_KEY_PARTY_ID = "partyId";
    private static final String PAYLOAD_KEY_SCHEME_CODE = "schemeCode";

    OutboxEventCandidate toCandidate(PartyOutboxEventEntity entity) {
        Objects.requireNonNull(entity, "entity");
        requireSchemaVersion(entity.eventSchemaVersion());
        return switch (entity.eventType()) {
            case PartyCreatedOutboxCandidate.EVENT_TYPE -> toPartyCreatedCandidate(entity);
            case PartyIdentifierCreatedOutboxCandidate.EVENT_TYPE -> toIdentifierCreatedCandidate(entity);
            case PartyActivatedOutboxCandidate.EVENT_TYPE -> toPartyActivatedCandidate(entity);
            default -> throw new IllegalStateException("Unsupported outbox event type");
        };
    }

    PartyOutboxEventEntity toEntity(OutboxEventCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        requireSchemaVersion(candidate.eventSchemaVersion());
        return switch (candidate) {
            case PartyCreatedOutboxCandidate event -> partyCreatedEntity(event);
            case PartyIdentifierCreatedOutboxCandidate event -> identifierCreatedEntity(event);
            case PartyActivatedOutboxCandidate event -> partyActivatedEntity(event);
        };
    }

    private static PartyOutboxEventEntity partyCreatedEntity(PartyCreatedOutboxCandidate event) {
        if (event.partyVersion().value() != 0) {
            throw new IllegalArgumentException("Party-created event must use aggregate version zero");
        }
        return pendingEntity(
                event,
                PartyOutboxAggregateType.PARTY,
                event.partyId().value(),
                event.partyVersion().value(),
                Map.of(PAYLOAD_KEY_PARTY_TYPE, event.partyType().name()));
    }

    private static PartyOutboxEventEntity identifierCreatedEntity(
            PartyIdentifierCreatedOutboxCandidate event) {
        if (event.identifierVersion().value() != 0
                || event.status() != PartyIdentifierStatus.PENDING_VERIFICATION) {
            throw new IllegalArgumentException(
                    "Identifier-created event must describe a pending version-zero identifier");
        }
        return pendingEntity(
                event,
                PartyOutboxAggregateType.PARTY_IDENTIFIER,
                event.identifierId().value(),
                event.identifierVersion().value(),
                Map.of(
                        PAYLOAD_KEY_PARTY_ID, event.partyId().value().toString(),
                        PAYLOAD_KEY_SCHEME_CODE, event.schemeCode(),
                        PAYLOAD_KEY_STATUS, event.status().name()));
    }

    private static PartyOutboxEventEntity partyActivatedEntity(PartyActivatedOutboxCandidate event) {
        if (event.partyVersion().value() <= 0 || event.status() != PartyRecordStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Party-activated event must describe an active Party after version zero");
        }
        return pendingEntity(
                event,
                PartyOutboxAggregateType.PARTY,
                event.partyId().value(),
                event.partyVersion().value(),
                Map.of(
                        PAYLOAD_KEY_PARTY_TYPE, event.partyType().name(),
                        PAYLOAD_KEY_STATUS, event.status().name()));
    }

    private static PartyOutboxEventEntity pendingEntity(
            OutboxEventCandidate event,
            PartyOutboxAggregateType aggregateType,
            UUID aggregateId,
            long aggregateVersion,
            Map<String, String> payload) {
        return PartyOutboxEventEntity.builder()
                .id(event.eventId())
                .tenantId(event.tenantId().value())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .aggregateVersion(aggregateVersion)
                .eventType(event.eventType())
                .eventSchemaVersion(event.eventSchemaVersion())
                .payload(payload)
                .occurredAt(event.occurredAt())
                .correlationId(event.correlationId().toString())
                .status(PartyOutboxStatus.PENDING)
                .publishAttempts(0)
                .createdAt(event.occurredAt())
                .createdBy(event.createdBy())
                .updatedAt(event.occurredAt())
                .updatedBy(event.createdBy())
                .version(0)
                .build();
    }

    private static PartyCreatedOutboxCandidate toPartyCreatedCandidate(
            PartyOutboxEventEntity entity) {
        requireAggregate(entity, PartyOutboxAggregateType.PARTY, 0);
        Map<String, String> payload = requirePayload(entity.payload(), Set.of(PAYLOAD_KEY_PARTY_TYPE));
        return new PartyCreatedOutboxCandidate(
                entity.id(),
                new TenantId(entity.tenantId()),
                new PartyId(entity.aggregateId()),
                new PartyVersion(entity.aggregateVersion()),
                PartyType.valueOf(payload.get(PAYLOAD_KEY_PARTY_TYPE)),
                entity.occurredAt(),
                correlationId(entity),
                entity.createdBy());
    }

    private static PartyIdentifierCreatedOutboxCandidate toIdentifierCreatedCandidate(
            PartyOutboxEventEntity entity) {
        requireAggregate(entity, PartyOutboxAggregateType.PARTY_IDENTIFIER, 0);
        Map<String, String> payload = requirePayload(
                entity.payload(),
                Set.of(PAYLOAD_KEY_PARTY_ID, PAYLOAD_KEY_SCHEME_CODE, PAYLOAD_KEY_STATUS));
        PartyIdentifierStatus status = PartyIdentifierStatus.valueOf(payload.get(PAYLOAD_KEY_STATUS));
        if (status != PartyIdentifierStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Identifier-created payload has an invalid status");
        }
        return new PartyIdentifierCreatedOutboxCandidate(
                entity.id(),
                new TenantId(entity.tenantId()),
                new PartyIdentifierId(entity.aggregateId()),
                new PartyIdentifierVersion(entity.aggregateVersion()),
                new PartyId(UUID.fromString(payload.get(PAYLOAD_KEY_PARTY_ID))),
                payload.get(PAYLOAD_KEY_SCHEME_CODE),
                status,
                entity.occurredAt(),
                correlationId(entity),
                entity.createdBy());
    }

    private static PartyActivatedOutboxCandidate toPartyActivatedCandidate(
            PartyOutboxEventEntity entity) {
        requireAggregate(entity, PartyOutboxAggregateType.PARTY, null);
        if (entity.aggregateVersion() <= 0) {
            throw new IllegalStateException("Party-activated event has an invalid aggregate version");
        }
        Map<String, String> payload = requirePayload(
                entity.payload(),
                Set.of(PAYLOAD_KEY_PARTY_TYPE, PAYLOAD_KEY_STATUS));
        PartyRecordStatus status = PartyRecordStatus.valueOf(payload.get(PAYLOAD_KEY_STATUS));
        if (status != PartyRecordStatus.ACTIVE) {
            throw new IllegalStateException("Party-activated payload has an invalid status");
        }
        return new PartyActivatedOutboxCandidate(
                entity.id(),
                new TenantId(entity.tenantId()),
                new PartyId(entity.aggregateId()),
                new PartyVersion(entity.aggregateVersion()),
                PartyType.valueOf(payload.get(PAYLOAD_KEY_PARTY_TYPE)),
                status,
                entity.occurredAt(),
                correlationId(entity),
                entity.createdBy());
    }

    private static void requireSchemaVersion(short schemaVersion) {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported outbox event schema version");
        }
    }

    private static void requireAggregate(
            PartyOutboxEventEntity entity,
            PartyOutboxAggregateType aggregateType,
            Integer exactVersion) {
        if (entity.aggregateType() != aggregateType
                || exactVersion != null && entity.aggregateVersion() != exactVersion) {
            throw new IllegalStateException("Outbox event aggregate metadata is inconsistent");
        }
    }

    private static Map<String, String> requirePayload(
            Map<String, String> payload,
            Set<String> expectedKeys) {
        if (!payload.keySet().equals(expectedKeys)
                || payload.values().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalStateException("Outbox event payload shape is inconsistent");
        }
        return payload;
    }

    private static UUID correlationId(PartyOutboxEventEntity entity) {
        if (entity.correlationId() == null) {
            throw new IllegalStateException("Outbox event correlation ID is missing");
        }
        return UUID.fromString(entity.correlationId());
    }
}
