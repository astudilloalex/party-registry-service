package com.alexastudillo.partyregistry.infrastructure.persistence;

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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies safe version-one candidate payloads and complete outbox column mapping.
 */
class PartyOutboxEventPersistenceMapperTest {

    private static final UUID EVENT_ID = UUID.fromString("01991a56-f000-7000-8000-000000000401");
    private static final TenantId TENANT_ID = new TenantId(
            UUID.fromString("01991a56-f000-7000-8000-000000000402"));
    private static final PartyId PARTY_ID = new PartyId(
            UUID.fromString("01991a56-f000-7000-8000-000000000403"));
    private static final PartyIdentifierId IDENTIFIER_ID = new PartyIdentifierId(
            UUID.fromString("01991a56-f000-7000-8000-000000000404"));
    private static final UUID CORRELATION_ID = UUID.fromString(
            "01991a56-f000-7000-8000-000000000405");
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-04T12:00:00.123456Z");

    private final PartyOutboxEventPersistenceMapper mapper = new PartyOutboxEventPersistenceMapper();

    @Test
    void roundTripsPartyCreatedCandidateWithTheExactV1Payload() {
        PartyCreatedOutboxCandidate candidate = new PartyCreatedOutboxCandidate(
                EVENT_ID,
                TENANT_ID,
                PARTY_ID,
                PartyVersion.initial(),
                PartyType.LEGAL_ENTITY,
                OCCURRED_AT,
                CORRELATION_ID,
                "operator");

        PartyOutboxEventEntity entity = mapper.toEntity(candidate);

        assertEquals(Map.of("partyType", "LEGAL_ENTITY"), entity.payload());
        assertEquals(candidate, mapper.toCandidate(entity));
        assertPendingDefaults(entity, PartyOutboxAggregateType.PARTY, PARTY_ID.value());
    }

    @Test
    void roundTripsIdentifierCreatedCandidateWithOnlySafePayloadFields() {
        PartyIdentifierCreatedOutboxCandidate candidate = new PartyIdentifierCreatedOutboxCandidate(
                EVENT_ID,
                TENANT_ID,
                IDENTIFIER_ID,
                PartyIdentifierVersion.initial(),
                PARTY_ID,
                "GB-PASSPORT",
                PartyIdentifierStatus.PENDING_VERIFICATION,
                OCCURRED_AT,
                CORRELATION_ID,
                "operator");

        PartyOutboxEventEntity entity = mapper.toEntity(candidate);

        assertEquals(Map.of(
                "partyId", PARTY_ID.value().toString(),
                "schemeCode", "GB-PASSPORT",
                "status", "PENDING_VERIFICATION"), entity.payload());
        assertEquals(candidate, mapper.toCandidate(entity));
        assertPendingDefaults(
                entity,
                PartyOutboxAggregateType.PARTY_IDENTIFIER,
                IDENTIFIER_ID.value());
        assertFalse(entity.payload().keySet().stream().anyMatch(key -> key.contains("value")
                || key.contains("hash")
                || key.contains("key")));
    }

    @Test
    void roundTripsPartyActivatedCandidateWithTheExactV1Payload() {
        PartyActivatedOutboxCandidate candidate = new PartyActivatedOutboxCandidate(
                EVENT_ID,
                TENANT_ID,
                PARTY_ID,
                new PartyVersion(3),
                PartyType.NATURAL_PERSON,
                PartyRecordStatus.ACTIVE,
                OCCURRED_AT,
                CORRELATION_ID,
                "operator");

        PartyOutboxEventEntity entity = mapper.toEntity(candidate);

        assertEquals(Map.of(
                "partyType", "NATURAL_PERSON",
                "status", "ACTIVE"), entity.payload());
        assertEquals(candidate, mapper.toCandidate(entity));
        assertPendingDefaults(entity, PartyOutboxAggregateType.PARTY, PARTY_ID.value());
    }

    @Test
    void mapsDeliveryMetadataAuditAndJpaVersionColumns() {
        Instant nextAttemptAt = OCCURRED_AT.plusSeconds(30);
        Instant lastAttemptAt = OCCURRED_AT.plusSeconds(10);
        Instant updatedAt = OCCURRED_AT.plusSeconds(11);
        PartyOutboxEventEntity entity = new PartyOutboxEventEntity(
                EVENT_ID,
                TENANT_ID.value(),
                PartyOutboxAggregateType.PARTY,
                PARTY_ID.value(),
                3,
                PartyActivatedOutboxCandidate.EVENT_TYPE,
                (short) 1,
                Map.of("partyType", "NATURAL_PERSON", "status", "ACTIVE"),
                OCCURRED_AT,
                CORRELATION_ID.toString(),
                "cause-1",
                PartyOutboxStatus.FAILED,
                4,
                nextAttemptAt,
                lastAttemptAt,
                null,
                "broker-unavailable",
                "sanitized detail",
                OCCURRED_AT,
                "creator",
                updatedAt,
                "publisher",
                5);

        assertEquals("cause-1", entity.causationId());
        assertEquals(PartyOutboxStatus.FAILED, entity.status());
        assertEquals(4, entity.publishAttempts());
        assertEquals(nextAttemptAt, entity.nextAttemptAt());
        assertEquals(lastAttemptAt, entity.lastAttemptAt());
        assertNull(entity.publishedAt());
        assertEquals("broker-unavailable", entity.lastErrorCode());
        assertEquals("sanitized detail", entity.lastErrorDetail());
        assertEquals(OCCURRED_AT, entity.createdAt());
        assertEquals("creator", entity.createdBy());
        assertEquals(updatedAt, entity.updatedAt());
        assertEquals("publisher", entity.updatedBy());
        assertEquals(5, entity.version());
    }

    @Test
    void enforcesCreatedEventStatesRequiredByTheVersionOneContract() {
        PartyCreatedOutboxCandidate invalidPartyEvent = new PartyCreatedOutboxCandidate(
                EVENT_ID,
                TENANT_ID,
                PARTY_ID,
                new PartyVersion(1),
                PartyType.NATURAL_PERSON,
                OCCURRED_AT,
                CORRELATION_ID,
                "operator");
        PartyIdentifierCreatedOutboxCandidate invalidIdentifierEvent = new PartyIdentifierCreatedOutboxCandidate(
                EVENT_ID,
                TENANT_ID,
                IDENTIFIER_ID,
                new PartyIdentifierVersion(1),
                PARTY_ID,
                "GB-PASSPORT",
                PartyIdentifierStatus.VERIFIED,
                OCCURRED_AT,
                CORRELATION_ID,
                "operator");

        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(invalidPartyEvent));
        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(invalidIdentifierEvent));
    }

    @Test
    void mapsOutboxEnumsAndVersionWithTheProjectConvention() throws NoSuchFieldException {
        assertNamedEnum("aggregateType");
        assertNamedEnum("status");
        assertTrue(PartyOutboxEventEntity.class.getDeclaredField("version")
                .isAnnotationPresent(Version.class));
    }

    private static void assertPendingDefaults(
            PartyOutboxEventEntity entity,
            PartyOutboxAggregateType aggregateType,
            UUID aggregateId) {
        assertEquals(EVENT_ID, entity.id());
        assertEquals(TENANT_ID.value(), entity.tenantId());
        assertEquals(aggregateType, entity.aggregateType());
        assertEquals(aggregateId, entity.aggregateId());
        assertEquals((short) 1, entity.eventSchemaVersion());
        assertEquals(OCCURRED_AT, entity.occurredAt());
        assertEquals(CORRELATION_ID.toString(), entity.correlationId());
        assertNull(entity.causationId());
        assertEquals(PartyOutboxStatus.PENDING, entity.status());
        assertEquals(0, entity.publishAttempts());
        assertNull(entity.nextAttemptAt());
        assertNull(entity.lastAttemptAt());
        assertNull(entity.publishedAt());
        assertNull(entity.lastErrorCode());
        assertNull(entity.lastErrorDetail());
        assertEquals(OCCURRED_AT, entity.createdAt());
        assertEquals("operator", entity.createdBy());
        assertEquals(OCCURRED_AT, entity.updatedAt());
        assertEquals("operator", entity.updatedBy());
        assertEquals(0, entity.version());
    }

    private static void assertNamedEnum(String fieldName) throws NoSuchFieldException {
        Field field = PartyOutboxEventEntity.class.getDeclaredField(fieldName);
        assertEquals(EnumType.STRING, field.getAnnotation(Enumerated.class).value());
        assertEquals(SqlTypes.NAMED_ENUM, field.getAnnotation(JdbcTypeCode.class).value());
    }
}
