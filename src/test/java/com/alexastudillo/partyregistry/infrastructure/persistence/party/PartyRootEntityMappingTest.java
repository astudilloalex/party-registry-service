package com.alexastudillo.partyregistry.infrastructure.persistence.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.eclipse.microprofile.config.ConfigProvider;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import com.alexastudillo.partyregistry.domain.party.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.party.model.PartyType;
import com.alexastudillo.partyregistry.infrastructure.persistence.PostgreSqlTestResource;
import com.alexastudillo.partyregistry.infrastructure.persistence.party.entity.PartyEntity;

import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Version;

@QuarkusTest
@QuarkusTestResource(value = PostgreSqlTestResource.class, restrictToAnnotatedClass = true)
class PartyRootEntityMappingTest {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    void mapsPostgresqlNamedEnumsAndReactiveUuidVersioning() throws ReflectiveOperationException {
        Field id = field("id");
        Field type = field("type");
        Field recordStatus = field("recordStatus");
        Field version = field("version");

        assertNotNull(id.getAnnotation(GeneratedValue.class));
        assertEquals(UuidGenerator.Style.VERSION_7, id.getAnnotation(UuidGenerator.class).style());
        assertNamedEnum(type, PartyType.class, "party_type");
        assertNamedEnum(recordStatus, PartyRecordStatus.class, "party_record_status");
        assertNotNull(version.getAnnotation(Version.class));
    }

    @Test
    @RunOnVertxContext
    void persistsUuidV7WithInitialVersionZero(UniAsserter asserter) throws ReflectiveOperationException {
        PartyEntity entity = new PartyEntity();
        Instant now = Instant.parse("2026-08-23T12:00:00Z");
        set(entity, "tenantId", UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
        set(entity, "type", PartyType.NATURAL_PERSON);
        set(entity, "displayName", "Ana Example");
        set(entity, "recordStatus", PartyRecordStatus.DRAFT);
        set(entity, "createdAt", now);
        set(entity, "createdBy", "test-subject");
        set(entity, "updatedAt", now);
        set(entity, "updatedBy", "test-subject");

        asserter.execute(() -> sessionFactory.withTransaction(
                (session, transaction) -> session.persist(entity).call(session::flush)));
        asserter.assertThat(
                () -> io.smallrye.mutiny.Uni.createFrom().item(entity),
                persisted -> {
                    assertNotNull(persisted.id());
                    assertEquals(7, persisted.id().version());
                    assertEquals(0L, persisted.version());
                });
    }

    @Test
    void validatesFlywaySchemaAndKeepsJdbcIsolated() {
        var config = ConfigProvider.getConfig();
        assertEquals("validate", config.getValue("quarkus.hibernate-orm.schema-management.strategy", String.class));
        assertFalse(config.getValue("quarkus.hibernate-orm.blocking", Boolean.class));
        assertFalse(config.getValue("quarkus.datasource.jdbc", Boolean.class));
        assertTrue(config.getValue("quarkus.flyway.flyway.migrate-at-start", Boolean.class));

        Instance<DataSource> defaultJdbc = CDI.current().select(DataSource.class);
        Instance<DataSource> flywayJdbc = CDI.current()
                .select(DataSource.class, new DataSourceLiteral("flyway"));
        assertFalse(defaultJdbc.isResolvable());
        assertTrue(flywayJdbc.isResolvable());
    }

    private static void assertNamedEnum(Field field, Class<?> expectedType, String databaseType) {
        assertEquals(expectedType, field.getType());
        assertEquals(EnumType.STRING, field.getAnnotation(Enumerated.class).value());
        assertEquals(SqlTypes.NAMED_ENUM, field.getAnnotation(JdbcTypeCode.class).value());
        assertEquals(databaseType, field.getAnnotation(Column.class).columnDefinition());
    }

    private static Field field(String name) throws NoSuchFieldException {
        return PartyEntity.class.getDeclaredField(name);
    }

    private static void set(PartyEntity entity, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = field(fieldName);
        field.setAccessible(true);
        field.set(entity, value);
    }

}
