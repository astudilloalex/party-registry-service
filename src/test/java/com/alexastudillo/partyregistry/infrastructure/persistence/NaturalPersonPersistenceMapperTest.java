package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies complete domain, entity, and idempotency-snapshot mappings.
 */
class NaturalPersonPersistenceMapperTest {

    private static final UUID PARTY_ID = UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1");
    private static final UUID TENANT_ID = UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5");
    private static final Instant CREATED_AT = Instant.parse("2026-08-29T10:15:30.123456Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-30T11:16:31.654321Z");

    private final NaturalPersonPersistenceMapper mapper = new NaturalPersonPersistenceMapper();

    @Test
    void roundTripsEveryNaturalPersonField() {
        NaturalPerson original = completeNaturalPerson();

        NaturalPerson restored = mapper.toDomain(mapper.toEntity(original));

        assertNaturalPersonEquals(original, restored);
    }

    @Test
    void roundTripsEverySnapshotField() {
        NaturalPersonResult original = NaturalPersonResult.fromAggregate(completeNaturalPerson());

        NaturalPersonResult restored = NaturalPersonResultSnapshot.from(original).toResult();

        assertEquals(original, restored);
    }

    @Test
    void preservesNullOptionalSnapshotFields() {
        NaturalPersonResult original = NaturalPersonResult.fromAggregate(NaturalPerson.restore(
                new PartyId(PARTY_ID),
                new TenantId(TENANT_ID),
                "Ada Lovelace",
                PartyRecordStatus.DRAFT,
                PartyVersion.initial(),
                AuditInfo.initial(CREATED_AT, "creator"),
                new NaturalPersonDetails("Ada", "Lovelace", null, null, null, null)));

        NaturalPersonResult restored = NaturalPersonResultSnapshot.from(original).toResult();

        assertNull(restored.preferredName());
        assertNull(restored.birthDate());
        assertNull(restored.dateOfDeath());
        assertNull(restored.birthCountryCode());
        assertEquals(original, restored);
    }

    @Test
    void mapsAggregateVersionThroughJpaVersionField() throws NoSuchFieldException {
        Field versionField = PartyEntity.class.getDeclaredField("version");

        assertTrue(versionField.isAnnotationPresent(Version.class));
        assertEquals(7, mapper.toEntity(completeNaturalPerson()).version());
    }

    private static NaturalPerson completeNaturalPerson() {
        return NaturalPerson.restore(
                new PartyId(PARTY_ID),
                new TenantId(TENANT_ID),
                "Ada Byron Lovelace",
                PartyRecordStatus.ACTIVE,
                new PartyVersion(7),
                new AuditInfo(CREATED_AT, "creator", UPDATED_AT, "updater"),
                new NaturalPersonDetails(
                        "Ada",
                        "Lovelace",
                        "Ada Byron",
                        LocalDate.parse("1815-12-10"),
                        LocalDate.parse("1852-11-27"),
                        "GB"));
    }

    private static void assertNaturalPersonEquals(NaturalPerson expected, NaturalPerson actual) {
        assertEquals(expected.partyId(), actual.partyId());
        assertEquals(expected.tenantId(), actual.tenantId());
        assertEquals(expected.type(), actual.type());
        assertEquals(expected.displayName(), actual.displayName());
        assertEquals(expected.recordStatus(), actual.recordStatus());
        assertEquals(expected.version(), actual.version());
        assertEquals(expected.auditInfo(), actual.auditInfo());
        assertEquals(expected.details(), actual.details());
    }
}
