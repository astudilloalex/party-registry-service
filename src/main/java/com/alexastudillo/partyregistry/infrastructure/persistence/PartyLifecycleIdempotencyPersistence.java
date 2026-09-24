package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.CompletedPartyLifecycle;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.infrastructure.observability.PartyPersistenceObservability;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToLongFunction;

/**
 * Serializes complete lifecycle replay scopes and stores only completed, validated historical results in the existing table.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyLifecycleIdempotencyPersistence {

    private final PartyLifecycleSnapshotCodec codec;
    private final ToLongFunction<ApiIdempotencyRecordId> lockIdentity;
    private final PartyPersistenceObservability observations;

    /** Uses a dedicated stable lock namespace, retaining full key identity independently of hash collisions. */
    @Inject
    public PartyLifecycleIdempotencyPersistence(PartyLifecycleSnapshotCodec codec, PartyPersistenceObservability observations) {
        this(codec, PartyLifecycleIdempotencyPersistence::lockId, observations);
    }

    PartyLifecycleIdempotencyPersistence(PartyLifecycleSnapshotCodec codec, ToLongFunction<ApiIdempotencyRecordId> lockIdentity,
            PartyPersistenceObservability observations) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.lockIdentity = Objects.requireNonNull(lockIdentity, "lockIdentity");
        this.observations = Objects.requireNonNull(observations, "observations");
    }

    Uni<Void> serializeReplayKey(Mutiny.Session session, TenantId tenant, PartyLifecycleAction action, String key) {
        long value = lockIdentity.applyAsLong(id(tenant, action, key));
        return observations.observeKeyWait(action, () -> session.createNativeQuery("select 1 from pg_advisory_xact_lock(:lockId)", Integer.class)
                .setParameter("lockId", value).getSingleResult().replaceWithVoid());
    }

    Uni<Optional<CompletedPartyLifecycle>> findCompleted(
            Mutiny.Session session, TenantId tenant, PartyLifecycleAction action, String key) {
        ApiIdempotencyRecordId identity = id(tenant, action, key);
        return session.find(ApiIdempotencyRecordEntity.class, identity).map(entry -> entry == null
                ? Optional.empty()
                : Optional.of(codec.decode(entry.resultSnapshot(), tenant, action, new PartyId(entry.partyId()), entry.requestHash())));
    }

    Uni<Void> recordCompletion(Mutiny.Session session, TenantId tenant, PartyLifecycleAction action, String key,
            CompletedPartyLifecycle completed) {
        if (!completed.result().tenantId().equals(tenant) || completed.request().action() != action) {
            throw new IllegalArgumentException("Completed lifecycle does not match its persistence scope");
        }
        var entry = new ApiIdempotencyRecordEntity(id(tenant, action, key),
                PartyLifecycleFingerprint.fingerprint(tenant, completed.request()), completed.result().partyId().value(),
                codec.encode(completed), completed.result().updatedAt(), completed.result().updatedBy());
        return session.persist(entry);
    }

    static String operation(PartyLifecycleAction action) {
        return switch (action) {
            case ACTIVATE -> "party.activate.v1";
            case DEACTIVATE -> "party.deactivate.v1";
            case ARCHIVE -> "party.archive.v1";
        };
    }

    static ApiIdempotencyRecordId id(TenantId tenant, PartyLifecycleAction action, String key) {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(action, "action");
        if (key == null || key.isBlank() || key.codePointCount(0, key.length()) > 128) {
            throw new IllegalArgumentException("Lifecycle replay key is invalid");
        }
        return new ApiIdempotencyRecordId(tenant.value(), operation(action), key);
    }

    static long lockId(ApiIdempotencyRecordId identity) {
        try (var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes)) {
            output.writeUTF("party-lifecycle-lock-v1");
            output.writeLong(identity.tenantId().getMostSignificantBits());
            output.writeLong(identity.tenantId().getLeastSignificantBits());
            output.writeUTF(identity.operation());
            output.writeInt(identity.idempotencyKey().length());
            output.writeChars(identity.idempotencyKey());
            output.flush();
            return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())).getLong();
        } catch (IOException | NoSuchAlgorithmException _) {
            throw new IllegalStateException("Lifecycle lock identity encoding failed");
        }
    }
}
