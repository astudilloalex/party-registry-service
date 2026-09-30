package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyChangedOutboxCandidate.Kind;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.infrastructure.messaging.OutboxMessage;
import com.alexastudillo.partyregistry.infrastructure.messaging.OutboxMessageSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies exact new root event shapes through storage and broker codecs, plus existing transactional storage modes. */
class PartyRootOutboxPersistenceTest {

    private static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atTime(12, 0, 0, 123456000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final PartyId PARTY_ID = new PartyId(UUID.randomUUID());
    private static final UUID PROCESS_ID = UUID.randomUUID();
    private static final PartyVersion VERSION = new PartyVersion(3);
    private final PartyOutboxEventPersistenceMapper mapper = new PartyOutboxEventPersistenceMapper();
    private final OutboxMessageSerializer serializer = new OutboxMessageSerializer(new ObjectMapper().registerModule(new JavaTimeModule()));

    @ParameterizedTest
    @EnumSource(Kind.class)
    void roundTripsEveryApplicableTypeAndStateWithOriginalMetadata(Kind kind) {
        for (PartyType type : PartyType.values()) {
            for (PartyRecordStatus status : PartyRecordStatus.values()) {
                if (!kind.accepts(status)) {
                    continue;
                }
                PartyChangedOutboxCandidate candidate = candidate(kind, type, status);
                PartyOutboxEventEntity stored = mapper.toEntity(candidate);
                assertEquals(candidate, mapper.toCandidate(stored));
                assertEquals(Map.of("partyType", type.name(), "status", status.name()), stored.payload());
                assertEquals(PARTY_ID.value(), stored.aggregateId());
                assertEquals(3, stored.aggregateVersion());
                assertEquals(CLOCK.instant(), stored.occurredAt());
                assertEquals(PROCESS_ID.toString(), stored.correlationId());
                assertEquals("original-actor", stored.createdBy());
                String wire = serializer.serialize(message(stored));
                assertFalse(wire.contains("displayName"));
                assertFalse(wire.contains("details"));
                assertFalse(wire.contains("encryptedValue"));
                assertEquals(wire, serializer.serialize(message(stored)));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void rejectsUnknownPayloadFieldsAndInvalidVersionsInBothCodecs(Kind kind) {
        PartyRecordStatus status = validStatus(kind);
        for (String field : List.of("displayName", "details", "encryptedValue", "normalizedValueHash", "keyMaterial")) {
            var invalid = row(kind, 3, (short) 1, Map.of("partyType", "LEGAL_ENTITY", "status", status.name(), field, "forbidden"));
            OutboxMessage wire = message(invalid);
            assertThrows(IllegalStateException.class, () -> mapper.toCandidate(invalid));
            assertThrows(IllegalArgumentException.class, () -> serializer.serialize(wire));
        }
        var zeroVersion = row(kind, 0, (short) 1, Map.of("partyType", "LEGAL_ENTITY", "status", status.name()));
        var wrongSchema = row(kind, 3, (short) 2, Map.of("partyType", "LEGAL_ENTITY", "status", status.name()));
        for (PartyOutboxEventEntity invalid : List.of(zeroVersion, wrongSchema)) {
            OutboxMessage wire = message(invalid);
            assertThrows(IllegalArgumentException.class, () -> mapper.toCandidate(invalid));
            assertThrows(IllegalArgumentException.class, () -> serializer.serialize(wire));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Kind.class, names = {"DEACTIVATED", "ARCHIVED"})
    void rejectsInconsistentResultingStatesBeforeStorageAndPublication(Kind kind) {
        for (PartyRecordStatus status : PartyRecordStatus.values()) {
            if (kind.accepts(status)) {
                continue;
            }
            assertThrows(IllegalArgumentException.class, () -> candidate(kind, PartyType.NATURAL_PERSON, status));
            var invalid = row(kind, 3, (short) 1, Map.of("partyType", "NATURAL_PERSON", "status", status.name()));
            OutboxMessage wire = message(invalid);
            assertThrows(IllegalArgumentException.class, () -> mapper.toCandidate(invalid));
            assertThrows(IllegalArgumentException.class, () -> serializer.serialize(wire));
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void storesEnabledEventsWithoutAnyBrokerCollaborator(Kind kind) {
        var candidate = candidate(kind, PartyType.NATURAL_PERSON, validStatus(kind));
        for (String mode : List.of("disabled", "stored-only", "published")) {
            var captured = new ArrayList<PartyOutboxEventEntity>();
            var session = recordingSession(captured);
            new PartyRootOutboxPersistence(mapper, mode).append(session, candidate).await().atMost(Duration.ofSeconds(2));
            if (mode.equals("disabled")) {
                assertEquals(List.of(), captured);
            } else {
                assertEquals(1, captured.size());
                assertEquals(candidate, mapper.toCandidate(captured.getFirst()));
                assertEquals(PartyOutboxStatus.PENDING, captured.getFirst().status());
                assertEquals(0, captured.getFirst().publishAttempts());
            }
        }
    }

    private static PartyChangedOutboxCandidate candidate(Kind kind, PartyType type, PartyRecordStatus status) {
        return new PartyChangedOutboxCandidate(EVENT_ID, TENANT, PARTY_ID, VERSION, type, status, kind,
                CLOCK.instant(), PROCESS_ID, "original-actor");
    }

    private static Mutiny.Session recordingSession(List<PartyOutboxEventEntity> captured) {
        Object proxy = Proxy.newProxyInstance(Mutiny.Session.class.getClassLoader(), new Class<?>[]{Mutiny.Session.class},
                (ignored, method, arguments) -> {
                    if (method.getName().equals("persist") && arguments != null && arguments.length == 1
                            && arguments[0] instanceof PartyOutboxEventEntity entity) {
                        captured.add(entity);
                        return Uni.createFrom().voidItem();
                    }
                    throw new AssertionError("Unexpected session operation: " + method.getName());
                });
        return Mutiny.Session.class.cast(proxy);
    }

    private static PartyRecordStatus validStatus(Kind kind) {
        return switch (kind) {
            case UPDATED -> PartyRecordStatus.DRAFT;
            case DEACTIVATED -> PartyRecordStatus.INACTIVE;
            case ARCHIVED -> PartyRecordStatus.ARCHIVED;
        };
    }

    private static PartyOutboxEventEntity row(Kind kind, long version, short schema, Map<String, String> payload) {
        return PartyOutboxEventEntity.builder().id(EVENT_ID).tenantId(TENANT.value()).aggregateType(PartyOutboxAggregateType.PARTY)
                .aggregateId(PARTY_ID.value()).aggregateVersion(version).eventType(kind.eventType()).eventSchemaVersion(schema)
                .payload(payload).occurredAt(CLOCK.instant()).correlationId(PROCESS_ID.toString()).createdAt(CLOCK.instant())
                .updatedAt(CLOCK.instant()).createdBy("original-actor").updatedBy("original-actor").status(PartyOutboxStatus.PENDING).build();
    }

    private static OutboxMessage message(PartyOutboxEventEntity stored) {
        return new OutboxMessage(stored.id(), stored.tenantId(), stored.aggregateType().name(), stored.aggregateId(),
                stored.aggregateVersion(), stored.eventType(), stored.eventSchemaVersion(), stored.payload(), stored.occurredAt(),
                stored.correlationId(), stored.causationId());
    }
}
