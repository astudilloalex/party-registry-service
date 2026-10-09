package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.reactive.mutiny.Mutiny;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Serializes identifier replay and primary scopes in distinct PostgreSQL two-integer transaction-lock domains. */
@ApplicationScoped
public class PartyIdentifierMutationLocks {

    static final int REPLAY_NAMESPACE = 0x5049524b;
    static final int SCOPE_NAMESPACE = 0x50495343;
    private final ToIntFunction<PartyIdentifierIdempotencyRecordId> replayIdentity;
    private final ToIntFunction<Scope> scopeIdentity;

    /** Uses bounded lock hashes only for serialization; full persisted identities still determine ownership and replay. */
    public PartyIdentifierMutationLocks() {
        this(PartyIdentifierMutationLocks::replayLockId, PartyIdentifierMutationLocks::scopeLockId);
    }

    PartyIdentifierMutationLocks(ToIntFunction<PartyIdentifierIdempotencyRecordId> replayIdentity, ToIntFunction<Scope> scopeIdentity) {
        this.replayIdentity = Objects.requireNonNull(replayIdentity, "replayIdentity");
        this.scopeIdentity = Objects.requireNonNull(scopeIdentity, "scopeIdentity");
    }

    Uni<Void> serializeReplayKey(Mutiny.Session session, PartyIdentifierIdempotencyRecordId identity) {
        return lock(session, REPLAY_NAMESPACE, replayIdentity.applyAsInt(identity));
    }

    Uni<Void> serializeScope(Mutiny.Session session, Scope scope) {
        return lock(session, SCOPE_NAMESPACE, scopeIdentity.applyAsInt(scope));
    }

    private static Uni<Void> lock(Mutiny.Session session, int namespace, int identity) {
        return session.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(:namespace, :identity)", Integer.class)
                .setParameter("namespace", namespace).setParameter("identity", identity).getSingleResult().replaceWithVoid();
    }

    static int replayLockId(PartyIdentifierIdempotencyRecordId identity) {
        return digest("party-identifier-replay-lock.v1", identity.tenantId().toString(), identity.operation(), identity.idempotencyKey());
    }

    static int scopeLockId(Scope scope) {
        return digest("party-identifier-primary-scope-lock.v1", scope.tenantId().toString(), scope.partyId().toString(), scope.schemeId().toString());
    }

    private static int digest(String... parts) {
        try (var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes)) {
            for (String part : parts) {
                output.writeInt(part.length());
                output.writeChars(part);
            }
            output.flush();
            return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())).getInt();
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Identifier lock identity encoding failed", failure);
        }
    }

    /** Carries the complete immutable tenant/owner/scheme tuple needed for writer serialization. */
    record Scope(UUID tenantId, UUID partyId, UUID schemeId) {
        Scope {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(partyId, "partyId");
            Objects.requireNonNull(schemeId, "schemeId");
        }
    }
}
