package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies protected PartyIdentifier persistence mapping without plaintext state.
 */
class PartyIdentifierPersistenceMapperTest {

    private static final PartyIdentifierId IDENTIFIER_ID = new PartyIdentifierId(
            UUID.fromString("01991a56-f000-7000-8000-000000000301"));
    private static final TenantId TENANT_ID = new TenantId(
            UUID.fromString("01991a56-f000-7000-8000-000000000302"));
    private static final PartyId PARTY_ID = new PartyId(
            UUID.fromString("01991a56-f000-7000-8000-000000000303"));
    private static final IdentifierSchemeId SCHEME_ID = new IdentifierSchemeId(
            UUID.fromString("01991a56-f000-7000-8000-000000000304"));
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T10:15:30.123456Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-09-02T11:16:31.654321Z");

    private final PartyIdentifierPersistenceMapper mapper = new PartyIdentifierPersistenceMapper();

    @Test
    void roundTripsProtectedValueValidityVerificationVersionAndAuditFields() {
        Instant verifiedAt = Instant.parse("2026-09-02T09:00:00Z");
        PartyIdentifier original = PartyIdentifier.restore(
                IDENTIFIER_ID,
                TENANT_ID,
                PARTY_ID,
                SCHEME_ID,
                protectedValue(),
                "HMPO",
                LocalDate.of(2025, 6, 1),
                LocalDate.of(2035, 6, 1),
                true,
                PartyIdentifierStatus.VERIFIED,
                verifiedAt,
                "verifier",
                new PartyIdentifierVersion(6),
                new AuditInfo(CREATED_AT, "creator", UPDATED_AT, "updater"));

        PartyIdentifierEntity entity = mapper.toEntity(original);
        PartyIdentifier restored = mapper.toDomain(entity);

        assertEquals("v1.authenticated-ciphertext", entity.encryptedValue());
        assertEquals((short) 7, entity.encryptionKeyVersion());
        assertEquals("0123456789abcdef".repeat(4), entity.normalizedValueHash());
        assertEquals((short) 3, entity.normalizationVersion());
        assertIdentifierEquals(original, restored);
    }

    @Test
    void preservesNullableIssuerValidityAndVerificationFields() {
        PartyIdentifier original = PartyIdentifier.create(
                IDENTIFIER_ID,
                TENANT_ID,
                PARTY_ID,
                SCHEME_ID,
                protectedValue(),
                null,
                null,
                null,
                CREATED_AT,
                "creator");

        PartyIdentifier restored = mapper.toDomain(mapper.toEntity(original));

        assertNull(restored.issuerCode());
        assertNull(restored.issuedOn());
        assertNull(restored.expiresOn());
        assertNull(restored.verifiedAt());
        assertNull(restored.verifiedBy());
        assertEquals(PartyIdentifierStatus.PENDING_VERIFICATION, restored.status());
        assertEquals(PartyIdentifierVersion.initial(), restored.version());
        assertIdentifierEquals(original, restored);
    }

    @Test
    void mapsStatusAsNamedEnumAndVersionAsJpaVersion() throws NoSuchFieldException {
        Field status = PartyIdentifierEntity.class.getDeclaredField("status");

        assertEquals(EnumType.STRING, status.getAnnotation(Enumerated.class).value());
        assertEquals(SqlTypes.NAMED_ENUM, status.getAnnotation(JdbcTypeCode.class).value());
        assertTrue(PartyIdentifierEntity.class.getDeclaredField("version")
                .isAnnotationPresent(Version.class));
    }

    @Test
    void persistenceEntityContainsNoPlaintextOrNormalizedPlaintextField() {
        assertFalse(Arrays.stream(PartyIdentifierEntity.class.getDeclaredFields())
                .map(Field::getName)
                .anyMatch(name -> name.equals("value")
                        || name.equals("completeValue")
                        || name.equals("normalizedValue")));
    }

    private static ProtectedIdentifierValue protectedValue() {
        return new ProtectedIdentifierValue(
                "v1.authenticated-ciphertext",
                7,
                "0123456789abcdef".repeat(4),
                "********1234",
                new IdentifierRuleVersion(3));
    }

    private static void assertIdentifierEquals(
            PartyIdentifier expected,
            PartyIdentifier actual) {
        assertEquals(expected.identifierId(), actual.identifierId());
        assertEquals(expected.tenantId(), actual.tenantId());
        assertEquals(expected.partyId(), actual.partyId());
        assertEquals(expected.identifierSchemeId(), actual.identifierSchemeId());
        assertEquals(expected.protectedValue(), actual.protectedValue());
        assertEquals(expected.issuerCode(), actual.issuerCode());
        assertEquals(expected.issuedOn(), actual.issuedOn());
        assertEquals(expected.expiresOn(), actual.expiresOn());
        assertEquals(expected.isPrimary(), actual.isPrimary());
        assertEquals(expected.status(), actual.status());
        assertEquals(expected.verifiedAt(), actual.verifiedAt());
        assertEquals(expected.verifiedBy(), actual.verifiedBy());
        assertEquals(expected.version(), actual.version());
        assertEquals(expected.auditInfo(), actual.auditInfo());
    }
}
