package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.CreateNationalityCommand;
import com.alexastudillo.partyregistry.application.command.SetPrimaryNationalityCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NationalityResult;
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
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Serializes tenant/action/key scopes before root locks and stores immutable successful nationality snapshots. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class NationalityIdempotencyPersistence {

    private final NationalityCreateSnapshotCodec creates;
    private final NationalityPrimarySnapshotCodec primaries;
    private final PartyPersistenceObservability observations;

    @Inject
    public NationalityIdempotencyPersistence(NationalityCreateSnapshotCodec creates,
            NationalityPrimarySnapshotCodec primaries, PartyPersistenceObservability observations) {
        this.creates = Objects.requireNonNull(creates, "creates");
        this.primaries = Objects.requireNonNull(primaries, "primaries");
        this.observations = Objects.requireNonNull(observations, "observations");
    }

    /** Takes one transaction-scoped lock on the full tenant, operation, and exact client key identity. */
    Uni<Void> serialize(Mutiny.Session session, TenantId tenant, NationalityOperation operation, String key) {
        long lock = lockId(id(tenant, operation, key));
        return observations.observeNationalityKeyWait(operation == NationalityOperation.CREATE ? "create" : "set-primary",
                () -> session.createNativeQuery("select 1 from pg_advisory_xact_lock(:lockId)", Integer.class)
                        .setParameter("lockId", lock).getSingleResult().replaceWithVoid());
    }

    /** Resolves completed creates before any Party state or geographic-reference check. */
    Uni<Optional<NationalityResult>> findCreate(Mutiny.Session session, CreateNationalityCommand command) {
        return session.find(ApiIdempotencyRecordEntity.class, id(command.requestMetadata().tenantId(),
                NationalityOperation.CREATE, command.idempotencyKey()))
                .map(stored -> stored == null ? Optional.empty()
                        : Optional.of(restoreCreate(stored, command)));
    }

    /** Resolves a completed primary transfer independently of current target eligibility. */
    Uni<Optional<NationalityResult>> findPrimary(Mutiny.Session session, SetPrimaryNationalityCommand command, String key) {
        return session.find(ApiIdempotencyRecordEntity.class, id(command.requestMetadata().tenantId(),
                NationalityOperation.SET_PRIMARY, key))
                .map(stored -> stored == null ? Optional.empty()
                        : Optional.of(restorePrimary(stored, command, key)));
    }

    /** Persists the committed create representation with its own schema version and effective fingerprint. */
    Uni<Void> recordCreate(Mutiny.Session session, CreateNationalityCommand command,
            NationalityResult result, Instant recordedAt) {
        return session.persist(new ApiIdempotencyRecordEntity(
                id(command.requestMetadata().tenantId(), NationalityOperation.CREATE, command.idempotencyKey()),
                NationalityRequestFingerprint.create(command), command.partyId().value(),
                creates.encode(command, result), recordedAt, command.requestMetadata().userId()));
    }

    /** Persists the original successful primary result, including an accepted keyed no-op. */
    Uni<Void> recordPrimary(Mutiny.Session session, SetPrimaryNationalityCommand command, String key,
            NationalityResult result, Instant recordedAt) {
        return session.persist(new ApiIdempotencyRecordEntity(
                id(command.requestMetadata().tenantId(), NationalityOperation.SET_PRIMARY, key),
                NationalityRequestFingerprint.setPrimary(command), command.partyId().value(),
                primaries.encode(command, result), recordedAt, command.requestMetadata().userId()));
    }

    private NationalityResult restoreCreate(ApiIdempotencyRecordEntity stored, CreateNationalityCommand command) {
        String hash = stored.requestHash();
        if (!NationalityRequestFingerprint.matches(hash, hash)) {
            throw NationalitySnapshotFields.invalid();
        }
        if (!NationalityRequestFingerprint.matches(hash, NationalityRequestFingerprint.create(command))) {
            throw conflict(command.idempotencyKey());
        }
        if (!stored.partyId().equals(command.partyId().value())) {
            throw NationalitySnapshotFields.invalid();
        }
        return creates.decode(stored.resultSnapshot(), command, hash);
    }

    private NationalityResult restorePrimary(ApiIdempotencyRecordEntity stored,
            SetPrimaryNationalityCommand command, String key) {
        String hash = stored.requestHash();
        if (!NationalityRequestFingerprint.matches(hash, hash)) {
            throw NationalitySnapshotFields.invalid();
        }
        if (!NationalityRequestFingerprint.matches(hash, NationalityRequestFingerprint.setPrimary(command))) {
            throw conflict(key);
        }
        if (!stored.partyId().equals(command.partyId().value())) {
            throw NationalitySnapshotFields.invalid();
        }
        return primaries.decode(stored.resultSnapshot(), command, hash);
    }

    static ApiIdempotencyRecordId id(TenantId tenant, NationalityOperation operation, String key) {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(operation, "operation");
        if (key == null || key.isBlank() || key.codePointCount(0, key.length()) > 128) {
            throw new IllegalArgumentException("Nationality replay key is invalid");
        }
        return new ApiIdempotencyRecordId(tenant.value(), operation.label(), key);
    }

    static long lockId(ApiIdempotencyRecordId identity) {
        try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) {
            out.writeUTF("nationality-idempotency-lock-v1");
            out.writeLong(identity.tenantId().getMostSignificantBits());
            out.writeLong(identity.tenantId().getLeastSignificantBits());
            out.writeUTF(identity.operation());
            out.writeInt(identity.idempotencyKey().length());
            out.writeChars(identity.idempotencyKey());
            out.flush();
            return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())).getLong();
        } catch (IOException | NoSuchAlgorithmException _) {
            throw new IllegalStateException("Nationality lock identity encoding failed");
        }
    }

    private static ApplicationException conflict(String key) {
        return new ApplicationException(new ApplicationFailure.IdempotencyKeyConflict(key));
    }
}
