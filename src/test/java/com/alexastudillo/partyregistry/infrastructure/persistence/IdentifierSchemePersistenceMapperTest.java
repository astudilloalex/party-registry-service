package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies complete identifier-scheme persistence mapping.
 */
class IdentifierSchemePersistenceMapperTest {

    private static final IdentifierSchemeId SCHEME_ID = new IdentifierSchemeId(
            UUID.fromString("01991a56-f000-7000-8000-000000000201"));
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T10:15:30.123456Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-09-03T11:16:31.654321Z");

    private final IdentifierSchemePersistenceMapper mapper = new IdentifierSchemePersistenceMapper();

    @Test
    void roundTripsRulesEnumsLengthsVersionAndAuditFields() {
        IdentifierScheme original = new IdentifierScheme(
                SCHEME_ID,
                "GB-COMPANY-NUMBER",
                "GB",
                IdentifierCategory.LEGAL_REGISTRATION_NUMBER,
                IdentifierSubjectType.LEGAL_ENTITY,
                "Company number",
                "Official legal registration number",
                "TRIM_UPPERCASE_V1",
                "ALPHANUMERIC_V1",
                8,
                12,
                true,
                IdentifierSchemeStatus.DEPRECATED,
                new IdentifierSchemeVersion(4),
                new AuditInfo(CREATED_AT, "catalog-creator", UPDATED_AT, "catalog-updater"));

        IdentifierSchemeEntity entity = mapper.toEntity(original);
        IdentifierScheme restored = mapper.toDomain(entity);

        assertEquals((short) 8, entity.minimumLength());
        assertEquals((short) 12, entity.maximumLength());
        assertEquals(IdentifierSchemeStatus.DEPRECATED, entity.status());
        assertEquals(4, entity.version());
        assertEquals(original, restored);
    }

    @Test
    void preservesNullableDescriptionAndLengthBounds() {
        IdentifierScheme original = new IdentifierScheme(
                SCHEME_ID,
                "GB-PASSPORT",
                "GB",
                IdentifierCategory.PASSPORT,
                IdentifierSubjectType.BOTH,
                "Passport",
                null,
                "TRIM_UPPERCASE_V1",
                "ALPHANUMERIC_V1",
                null,
                null,
                false,
                IdentifierSchemeStatus.ACTIVE,
                IdentifierSchemeVersion.initial(),
                AuditInfo.initial(CREATED_AT, "catalog"));

        IdentifierScheme restored = mapper.toDomain(mapper.toEntity(original));

        assertNull(restored.description());
        assertNull(restored.minimumLength());
        assertNull(restored.maximumLength());
        assertEquals(original, restored);
    }

    @Test
    void rejectsLengthValuesThatCannotFitTheV1SmallintColumns() {
        IdentifierScheme oversized = new IdentifierScheme(
                SCHEME_ID,
                "OVERSIZED",
                "GB",
                IdentifierCategory.OTHER,
                IdentifierSubjectType.BOTH,
                "Oversized",
                null,
                "TRIM_V1",
                "NON_BLANK_V1",
                1,
                Short.MAX_VALUE + 1,
                false,
                IdentifierSchemeStatus.DRAFT,
                IdentifierSchemeVersion.initial(),
                AuditInfo.initial(CREATED_AT, "catalog"));

        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(oversized));
    }

    @Test
    void mapsPostgresEnumsAndOptimisticVersionWithTheProjectConvention()
            throws NoSuchFieldException {
        assertNamedEnum("category");
        assertNamedEnum("applicableSubjectType");
        assertNamedEnum("status");
        assertTrue(IdentifierSchemeEntity.class.getDeclaredField("version")
                .isAnnotationPresent(Version.class));
    }

    private static void assertNamedEnum(String fieldName) throws NoSuchFieldException {
        Field field = IdentifierSchemeEntity.class.getDeclaredField(fieldName);
        assertEquals(EnumType.STRING, field.getAnnotation(Enumerated.class).value());
        assertEquals(SqlTypes.NAMED_ENUM, field.getAnnotation(JdbcTypeCode.class).value());
    }
}
