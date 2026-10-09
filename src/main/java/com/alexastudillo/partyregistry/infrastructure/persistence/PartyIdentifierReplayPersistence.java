package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Objects;
import java.util.Optional;

/** Persists only completed immutable identifier history within the caller-owned reactive mutation session. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyIdentifierReplayPersistence {

    private final PartyIdentifierCompletionCodec codec;

    @Inject
    public PartyIdentifierReplayPersistence(PartyIdentifierCompletionCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    Uni<Optional<CompletedPartyIdentifierMutation>> findCompleted(Mutiny.Session session, PartyIdentifierIdempotencyRecordId identity) {
        return session.find(PartyIdentifierIdempotencyRecordEntity.class, identity)
                .map(entity -> Optional.ofNullable(entity).map(codec::decode));
    }

    Uni<Void> recordCompletion(Mutiny.Session session, PartyIdentifierIdempotencyRecordId identity,
            CompletedPartyIdentifierMutation completed) {
        return session.persist(new PartyIdentifierIdempotencyRecordEntity(identity, completed, codec.encode(completed)));
    }
}
