package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.CompletedIdentifierSchemeOperation;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Objects;
import java.util.Optional;

/** Stores original immutable completion history atomically in the scheme transaction and exact replay tuple. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class IdentifierSchemeReplayPersistence {
    private final IdentifierSchemeCompletionCodec codec;

    /** Uses the explicit versioned codec, never an API or managed-entity snapshot. */
    @Inject
    public IdentifierSchemeReplayPersistence(IdentifierSchemeCompletionCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    Uni<Optional<CompletedIdentifierSchemeOperation>> findCompleted(Mutiny.Session session, IdentifierSchemeIdempotencyRecordId identity) {
        return session.find(IdentifierSchemeIdempotencyRecordEntity.class, identity)
                .map(entity -> Optional.ofNullable(entity).map(codec::decode));
    }

    Uni<Void> recordCompletion(Mutiny.Session session, IdentifierSchemeIdempotencyRecordId identity,
            CompletedIdentifierSchemeOperation completed) {
        if (!identity.operation().equals(completed.request().operation())) {
            throw new IllegalArgumentException("Completed operation does not match its replay namespace");
        }
        var tenant = new TenantId(identity.tenantId());
        var snapshot = codec.encode(tenant, completed);
        var result = completed.result();
        var entity = new IdentifierSchemeIdempotencyRecordEntity(identity,
                IdentifierSchemeRequestFingerprint.fingerprint(tenant, completed.request()), result.id().value(),
                snapshot.schemaVersion(), snapshot.payload(), result.updatedAt(), result.updatedBy());
        return session.persist(entity);
    }
}
