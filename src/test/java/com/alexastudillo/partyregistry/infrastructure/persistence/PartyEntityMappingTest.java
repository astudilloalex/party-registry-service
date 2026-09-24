package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies Party detail shape mappings and PartyIdentifier persistence independence.
 */
class PartyEntityMappingTest {

    private static final Map<Class<?>, Set<String>> EXPECTED_COLUMNS = Map.of(
            PartyEntity.class,
            Set.of(
                    "id", "tenant_id", "type", "display_name", "record_status",
                    "created_at", "created_by", "updated_at", "updated_by", "version"),
            NaturalPersonDetailsEntity.class,
            Set.of(
                    "party_id", "given_names", "family_names", "preferred_name", "birth_date",
                    "date_of_death", "birth_country_code", "created_at", "created_by",
                    "updated_at", "updated_by"),
            LegalEntityDetailsEntity.class,
            Set.of(
                    "party_id", "legal_name", "trade_name", "legal_form_code",
                    "incorporation_country_code", "incorporated_on", "dissolved_on",
                    "created_at", "created_by", "updated_at", "updated_by"),
            IdentifierSchemeEntity.class,
            Set.of(
                    "id", "code", "issuing_country_code", "category", "applicable_subject_type",
                    "name", "description", "normalizer_key", "validator_key", "minimum_length",
                    "maximum_length", "requires_expiration", "status", "created_at", "created_by",
                    "updated_at", "updated_by", "version"),
            PartyIdentifierEntity.class,
            Set.of(
                    "id", "tenant_id", "party_id", "identifier_scheme_id", "issuer_code",
                    "encrypted_value", "encryption_key_version", "normalized_value_hash",
                    "masked_value", "normalization_version", "is_primary", "status", "issued_on",
                    "expires_on", "verified_at", "verified_by", "created_at", "created_by",
                    "updated_at", "updated_by", "version"),
            PartyOutboxEventEntity.class,
            Set.of(
                    "id", "tenant_id", "aggregate_type", "aggregate_id", "aggregate_version",
                    "event_type", "event_schema_version", "payload", "occurred_at", "correlation_id",
                    "causation_id", "status", "publish_attempts", "next_attempt_at", "last_attempt_at",
                    "published_at", "last_error_code", "last_error_detail", "created_at", "created_by",
                    "updated_at", "updated_by", "version"));

    private static final Map<Class<?>, String> EXPECTED_TABLES = Map.of(
            PartyEntity.class, "parties",
            NaturalPersonDetailsEntity.class, "natural_person_details",
            LegalEntityDetailsEntity.class, "legal_entity_details",
            IdentifierSchemeEntity.class, "identifier_schemes",
            PartyIdentifierEntity.class, "party_identifiers",
            PartyOutboxEventEntity.class, "party_outbox_events");

    @Test
    void partyRootHasExactlyTheTwoTypeSpecificDetailAssociations() {
        Set<Field> associations = Arrays.stream(PartyEntity.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(OneToOne.class))
                .collect(Collectors.toSet());

        assertEquals(Set.of(NaturalPersonDetailsEntity.class, LegalEntityDetailsEntity.class),
                associations.stream().map(Field::getType).collect(Collectors.toSet()));
        associations.forEach(field -> {
            OneToOne mapping = field.getAnnotation(OneToOne.class);
            assertEquals("party", mapping.mappedBy());
            assertArrayEquals(new CascadeType[] { CascadeType.PERSIST }, mapping.cascade());
        });
    }

    @Test
    void partyRootHasNoIdentifierFieldCollectionOrAssociation() {
        assertFalse(Arrays.stream(PartyEntity.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == PartyIdentifierEntity.class
                        || Collection.class.isAssignableFrom(field.getType())
                        || field.getName().toLowerCase().contains("identifier")));
        assertFalse(Arrays.stream(PartyEntity.class.getDeclaredFields())
                .anyMatch(field -> field.isAnnotationPresent(OneToMany.class)
                        || field.isAnnotationPresent(ManyToMany.class)
                        || field.isAnnotationPresent(ManyToOne.class)));
    }

    @Test
    void partyRejectsDetailsThatDoNotMatchItsType() {
        UUID partyId = UUID.fromString("0194712f-df4a-7a5e-a416-4f6f3a621004");
        UUID tenantId = UUID.fromString("0194712f-df4a-7a5e-a416-4f6f3a621005");
        AuditInfo auditInfo = AuditInfo.initial(Instant.parse("2026-01-04T00:00:00Z"), "creator");
        var naturalPerson = new PartyEntity(
                partyId, tenantId, PartyType.NATURAL_PERSON, "Ada Lovelace",
                PartyRecordStatus.DRAFT, auditInfo, 0L);
        var legalEntity = new PartyEntity(
                partyId, tenantId, PartyType.LEGAL_ENTITY, "Analytical Engines Ltd",
                PartyRecordStatus.DRAFT, auditInfo, 0L);

        assertThrows(IllegalStateException.class, () -> naturalPerson.attachLegalEntityDetails(
                new LegalEntityDetailsEntity(
                        partyId,
                        new LegalEntityDetails("Analytical Engines Ltd", null, null, "GB", null, null),
                        auditInfo)));
        assertThrows(IllegalStateException.class, () -> legalEntity.attachNaturalPersonDetails(
                new NaturalPersonDetailsEntity(
                        partyId,
                        new NaturalPersonDetails("Ada", "Lovelace", null, null, null, null),
                        auditInfo)));
    }

    @Test
    void identifierEntityUsesForeignKeyColumnsWithoutAggregateAssociations() {
        assertFalse(Arrays.stream(PartyIdentifierEntity.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == PartyEntity.class
                        || field.isAnnotationPresent(OneToOne.class)
                        || field.isAnnotationPresent(OneToMany.class)
                        || field.isAnnotationPresent(ManyToOne.class)
                        || field.isAnnotationPresent(ManyToMany.class)));
    }

    @Test
    void mapsEveryRequiredVersionOneColumnExactlyOnce() {
        EXPECTED_COLUMNS.forEach((entityType, expectedColumns) -> assertEquals(
                expectedColumns,
                Arrays.stream(entityType.getDeclaredFields())
                        .map(field -> field.getAnnotation(Column.class))
                        .filter(java.util.Objects::nonNull)
                        .map(Column::name)
                        .collect(Collectors.toSet()),
                entityType.getSimpleName()));
    }

    @Test
    void mapsEachEntityToItsVersionOneTable() {
        EXPECTED_TABLES.forEach((entityType, tableName) -> assertEquals(
                tableName,
                entityType.getAnnotation(Table.class).name(),
                entityType.getSimpleName()));
    }

    @Test
    void everyPersistenceEntityHasAProtectedHibernateConstructor() throws NoSuchMethodException {
        for (Class<?> entityType : EXPECTED_COLUMNS.keySet()) {
            Constructor<?> constructor = entityType.getDeclaredConstructor();
            assertFalse(Modifier.isPublic(constructor.getModifiers()), entityType.getSimpleName());
            assertFalse(Modifier.isPrivate(constructor.getModifiers()), entityType.getSimpleName());
        }
    }
}
