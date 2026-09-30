package com.alexastudillo.partyregistry.support;

import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/** Seeds deterministic root/detail test data through reactive DML in the existing Flyway-managed schema. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class RootPartyFixtures {

    @Inject
    Mutiny.SessionFactory sessions;

    /** Stores a retained version-zero Party without geographic or identifier dependencies and returns its identity. */
    public Uni<UUID> create(UUID tenant, PartyType type, PartyRecordStatus status, String label, Instant created) {
        UUID id = UUID.randomUUID();
        return sessions.withTransaction((session, transaction) -> session.createNativeQuery("""
                insert into parties (id, tenant_id, type, display_name, record_status, version,
                                     created_at, updated_at, created_by, updated_by)
                values (:id, :tenant, cast(:type as party_type), :label, cast(:status as party_record_status), 0,
                        :created, :created, 'fixture-creator', 'fixture-creator')
                """).setParameter("id", id).setParameter("tenant", tenant).setParameter("type", type.name())
                .setParameter("label", label).setParameter("status", status.name()).setParameter("created", created).executeUpdate()
                .chain(() -> session.createNativeQuery(detailsSql(type)).setParameter("id", id).setParameter("created", created)
                        .executeUpdate()).replaceWith(id));
    }

    private static String detailsSql(PartyType type) {
        return switch (type) {
            case NATURAL_PERSON -> """
                    insert into natural_person_details (party_id, given_names, family_names, birth_country_code,
                                                        created_at, updated_at, created_by, updated_by)
                    values (:id, 'Historical', 'Person', 'GB', :created, :created, 'fixture-creator', 'fixture-creator')
                    """;
            case LEGAL_ENTITY -> """
                    insert into legal_entity_details (party_id, legal_name, incorporation_country_code,
                                                      created_at, updated_at, created_by, updated_by)
                    values (:id, 'Historical Company', 'GB', :created, :created, 'fixture-creator', 'fixture-creator')
                    """;
        };
    }

    /** Runs bounded setup on a Vert.x context while ordinary HTTP test code remains off the event loop. */
    public static <T> T await(Supplier<Uni<T>> operation) {
        try {
            return VertxContextSupport.subscribeAndAwait(() -> operation.get().ifNoItem().after(Duration.ofSeconds(15)).fail());
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new AssertionError("Reactive root fixture operation failed", failure);
        }
    }
}
