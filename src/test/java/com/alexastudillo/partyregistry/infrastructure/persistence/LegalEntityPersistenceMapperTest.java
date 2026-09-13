package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies legal-entity aggregate and detail persistence mapping.
 */
class LegalEntityPersistenceMapperTest {

    private static final PartyId PARTY_ID = new PartyId(
            UUID.fromString("01991a56-f000-7000-8000-000000000101"));
    private static final TenantId TENANT_ID = new TenantId(
            UUID.fromString("01991a56-f000-7000-8000-000000000102"));
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T10:15:30.123456Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-09-02T11:16:31.654321Z");

    private final LegalEntityPersistenceMapper mapper = new LegalEntityPersistenceMapper();

    @Test
    void roundTripsEveryLegalEntityAndAuditField() {
        LegalEntity original = LegalEntity.restore(
                PARTY_ID,
                TENANT_ID,
                "Analytical Engines",
                PartyRecordStatus.ACTIVE,
                new PartyVersion(9),
                new AuditInfo(CREATED_AT, "creator", UPDATED_AT, "updater"),
                new LegalEntityDetails(
                        "Analytical Engines Ltd.",
                        "Analytical Engines",
                        "LTD",
                        "GB",
                        LocalDate.of(1843, 1, 1),
                        LocalDate.of(1900, 12, 31)));

        PartyEntity entity = mapper.toEntity(original);
        LegalEntity restored = mapper.toDomain(entity);

        assertEquals(PARTY_ID.value(), entity.id());
        assertNotNull(entity.legalEntityDetails());
        assertNull(entity.naturalPersonDetails());
        assertLegalEntityEquals(original, restored);
    }

    @Test
    void preservesNullableLegalEntityDetailFields() {
        LegalEntity original = LegalEntity.restore(
                PARTY_ID,
                TENANT_ID,
                "Analytical Engines Ltd.",
                PartyRecordStatus.DRAFT,
                PartyVersion.initial(),
                AuditInfo.initial(CREATED_AT, "creator"),
                new LegalEntityDetails(
                        "Analytical Engines Ltd.",
                        null,
                        null,
                        "GB",
                        null,
                        null));

        LegalEntity restored = mapper.toDomain(mapper.toEntity(original));

        assertNull(restored.details().tradeName());
        assertNull(restored.details().legalFormCode());
        assertNull(restored.details().incorporatedOn());
        assertNull(restored.details().dissolvedOn());
        assertLegalEntityEquals(original, restored);
    }

    private static void assertLegalEntityEquals(LegalEntity expected, LegalEntity actual) {
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
