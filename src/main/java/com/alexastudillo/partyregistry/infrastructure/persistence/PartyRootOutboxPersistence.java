package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyActivatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyChangedOutboxCandidate;
import com.alexastudillo.partyregistry.infrastructure.configuration.PartyOutboxPersistenceMode;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Objects;

/** Stores enabled safe root events in the caller's transaction without waiting for broker delivery. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyRootOutboxPersistence {

    private final PartyOutboxEventPersistenceMapper mapper;
    private final PartyOutboxPersistenceMode mode;

    /** Uses the existing disabled, stored-only, or published mode; publication remains asynchronous. */
    @Inject
    public PartyRootOutboxPersistence(PartyOutboxEventPersistenceMapper mapper,
            @ConfigProperty(name = "party-registry.outbox.mode") String mode) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.mode = PartyOutboxPersistenceMode.fromConfiguration(mode);
    }

    Uni<Void> append(Mutiny.Session session, OutboxEventCandidate candidate) {
        return Uni.createFrom().deferred(() -> {
            if (!(candidate instanceof PartyChangedOutboxCandidate) && !(candidate instanceof PartyActivatedOutboxCandidate)) {
                throw new IllegalArgumentException("Root mutation requires a root change event");
            }
            PartyOutboxEventEntity entity = mapper.toEntity(candidate);
            return mode.storesEvents() ? session.persist(entity) : Uni.createFrom().voidItem();
        });
    }
}
