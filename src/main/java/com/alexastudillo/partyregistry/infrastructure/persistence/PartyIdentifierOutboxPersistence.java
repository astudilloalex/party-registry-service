package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.infrastructure.configuration.PartyOutboxPersistenceMode;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Objects;

/** Stores enabled safe identifier event intent in the caller's transaction without a broker operation. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyIdentifierOutboxPersistence {

    private final PartyOutboxEventPersistenceMapper mapper;
    private final PartyOutboxPersistenceMode mode;

    /** Applies the existing disabled, stored-only and published policies; delivery remains asynchronous. */
    @Inject
    public PartyIdentifierOutboxPersistence(PartyOutboxEventPersistenceMapper mapper, @ConfigProperty(name = "party-registry.outbox.mode") String mode) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.mode = PartyOutboxPersistenceMode.fromConfiguration(mode);
    }

    Uni<Void> append(Mutiny.Session session, OutboxEventCandidate candidate) {
        return Uni.createFrom().deferred(() -> {
            if (!(candidate instanceof PartyIdentifierCreatedOutboxCandidate) && !(candidate instanceof PartyIdentifierChangedOutboxCandidate)) {
                throw new IllegalArgumentException("Identifier mutation requires an identifier event");
            }
            var entity = mapper.toEntity(candidate);
            return mode.storesEvents() ? session.persist(entity) : Uni.createFrom().voidItem();
        });
    }
}
