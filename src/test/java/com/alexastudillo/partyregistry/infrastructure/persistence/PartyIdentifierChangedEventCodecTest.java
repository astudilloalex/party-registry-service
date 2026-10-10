package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies new schema-one identifier event metadata/allowlists and unchanged creation-event compatibility. */
class PartyIdentifierChangedEventCodecTest {

    private static final Instant OCCURRED = LocalDate.of(2026, Month.OCTOBER, 4).atStartOfDay(ZoneOffset.UTC).toInstant();
    private final PartyOutboxEventPersistenceMapper mapper = new PartyOutboxEventPersistenceMapper();

    @Test
    void roundTripsEveryNewEventAndBothPrimaryChangeFlagsWithOnlySafeFields() {
        for (var kind : PartyIdentifierChangedOutboxCandidate.Kind.values()) {
            var candidate = candidate(kind, false);
            var stored = mapper.toEntity(candidate);
            assertEquals(Set.of("partyId", "schemeCode", "status", "isPrimary"), stored.payload().keySet());
            assertEquals("false", stored.payload().get("isPrimary"));
            assertEquals(candidate, mapper.toCandidate(stored));
            assertEquals(candidate.identifierId().value(), stored.aggregateId());
            assertEquals(9, stored.aggregateVersion());
            assertEquals("original-actor", stored.createdBy());
            assertEquals(OCCURRED, stored.occurredAt());
        }
        var selected = candidate(PartyIdentifierChangedOutboxCandidate.Kind.PRIMARY_CHANGED, true);
        assertEquals(selected, mapper.toCandidate(mapper.toEntity(selected)));
    }

    @Test
    void rejectsCorruptStoredPayloadWithoutRawContentOrParserCause() {
        var candidate = candidate(PartyIdentifierChangedOutboxCandidate.Kind.VERIFIED, false);
        var valid = mapper.toEntity(candidate);
        for (Map<String, String> payload : List.of(changed(valid.payload(), "reasonDetail", "sensitive-event-content"),
                changed(valid.payload(), "status", "sensitive-event-content"), changed(valid.payload(), "isPrimary", "TRUE"),
                changed(valid.payload(), "partyId", "not-a-uuid"))) {
            var corrupt = stored(candidate, payload, 9);
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> mapper.toCandidate(corrupt));
            assertEquals("Stored identifier event is invalid", failure.getMessage());
            assertNull(failure.getCause());
        }
        var invalidVersion = stored(candidate, valid.payload(), 0);
        assertThrows(IllegalStateException.class, () -> mapper.toCandidate(invalidVersion));
    }

    @Test
    void preservesTheExactExistingCreatedPayloadWithoutAddingAPrimaryField() {
        var changed = candidate(PartyIdentifierChangedOutboxCandidate.Kind.UPDATED, false);
        var created = new PartyIdentifierCreatedOutboxCandidate(changed.eventId(), changed.tenantId(), changed.identifierId(), new PartyIdentifierVersion(0),
                changed.partyId(), changed.schemeCode(), PartyIdentifierStatus.PENDING_VERIFICATION, OCCURRED, changed.correlationId(), changed.createdBy());
        var stored = mapper.toEntity(created);
        assertEquals(Set.of("partyId", "schemeCode", "status"), stored.payload().keySet());
        assertEquals(created, mapper.toCandidate(stored));
    }

    @Test
    void storesOnlyEnabledIdentifierEventsWithoutABrokerCollaborator() {
        var event = candidate(PartyIdentifierChangedOutboxCandidate.Kind.REVOKED, false);
        for (String mode : List.of("disabled", "stored-only", "published")) {
            var captured = new ArrayList<PartyOutboxEventEntity>();
            var session = recordingSession(captured);
            new PartyIdentifierOutboxPersistence(mapper, mode).append(session, event).await().atMost(Duration.ofSeconds(2));
            assertEquals(mode.equals("disabled") ? 0 : 1, captured.size());
            if (!captured.isEmpty()) {
                assertEquals(event, mapper.toCandidate(captured.getFirst()));
                assertEquals(PartyOutboxStatus.PENDING, captured.getFirst().status());
                assertEquals(0, captured.getFirst().publishAttempts());
            }
        }
    }

    private static Mutiny.Session recordingSession(List<PartyOutboxEventEntity> captured) {
        Object proxy = Proxy.newProxyInstance(Mutiny.Session.class.getClassLoader(), new Class<?>[]{Mutiny.Session.class}, (ignored, method, arguments) -> {
            if (method.getName().equals("persist") && arguments != null && arguments.length == 1 && arguments[0] instanceof PartyOutboxEventEntity entity) {
                captured.add(entity);
                return Uni.createFrom().voidItem();
            }
            throw new AssertionError("Unexpected identifier event session operation");
        });
        return Mutiny.Session.class.cast(proxy);
    }

    private static PartyIdentifierChangedOutboxCandidate candidate(PartyIdentifierChangedOutboxCandidate.Kind kind, boolean primary) {
        PartyIdentifierStatus status = switch (kind) {
            case UPDATED -> PartyIdentifierStatus.PENDING_VERIFICATION;
            case VERIFIED, PRIMARY_CHANGED -> PartyIdentifierStatus.VERIFIED;
            case REJECTED -> PartyIdentifierStatus.REJECTED;
            case REVOKED -> PartyIdentifierStatus.REVOKED;
        };
        return new PartyIdentifierChangedOutboxCandidate(UUID.randomUUID(), new TenantId(UUID.randomUUID()), new PartyIdentifierId(UUID.randomUUID()),
                new PartyIdentifierVersion(9), new PartyId(UUID.randomUUID()), "FIXTURE_SCHEME", status, primary, kind, OCCURRED, UUID.randomUUID(), "original-actor");
    }

    private static Map<String, String> changed(Map<String, String> original, String key, String value) {
        var payload = new HashMap<>(original);
        payload.put(key, value);
        return payload;
    }

    private static PartyOutboxEventEntity stored(PartyIdentifierChangedOutboxCandidate candidate, Map<String, String> payload, long version) {
        return PartyOutboxEventEntity.builder().id(candidate.eventId()).tenantId(candidate.tenantId().value()).aggregateType(PartyOutboxAggregateType.PARTY_IDENTIFIER)
                .aggregateId(candidate.identifierId().value()).aggregateVersion(version).eventType(candidate.eventType()).eventSchemaVersion((short) 1)
                .payload(payload).occurredAt(OCCURRED).correlationId(candidate.correlationId().toString()).createdAt(OCCURRED).createdBy(candidate.createdBy())
                .updatedAt(OCCURRED).updatedBy(candidate.createdBy()).build();
    }
}
