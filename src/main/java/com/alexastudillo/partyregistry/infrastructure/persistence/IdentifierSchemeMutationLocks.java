package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.reactive.mutiny.Mutiny;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.function.ToLongFunction;

/** Serializes full replay tuples and exact global codes in distinct versioned transaction-lock domains. */
@ApplicationScoped
public class IdentifierSchemeMutationLocks {
    private final ToLongFunction<IdentifierSchemeIdempotencyRecordId> replayIdentity;
    private final ToLongFunction<String> codeIdentity;

    /** Keeps truncated lock digests separate from full persisted identities and exact code comparisons. */
    public IdentifierSchemeMutationLocks() {
        this(IdentifierSchemeMutationLocks::replayLockId, IdentifierSchemeMutationLocks::codeLockId);
    }

    IdentifierSchemeMutationLocks(ToLongFunction<IdentifierSchemeIdempotencyRecordId> replayIdentity,
            ToLongFunction<String> codeIdentity) {
        this.replayIdentity = Objects.requireNonNull(replayIdentity, "replayIdentity");
        this.codeIdentity = Objects.requireNonNull(codeIdentity, "codeIdentity");
    }

    Uni<Void> serializeReplayKey(Mutiny.Session session, IdentifierSchemeIdempotencyRecordId id) {
        return lock(session, replayIdentity.applyAsLong(id));
    }

    Uni<Void> serializeCode(Mutiny.Session session, String code) {
        return lock(session, codeIdentity.applyAsLong(code));
    }

    private static Uni<Void> lock(Mutiny.Session session, long identity) {
        return session.createNativeQuery("select 1 from pg_advisory_xact_lock(:identity)", Integer.class)
                .setParameter("identity", identity).getSingleResult().replaceWithVoid();
    }

    static long replayLockId(IdentifierSchemeIdempotencyRecordId id) {
        return digest("identifier-scheme-replay-lock.v1", id.tenantId().toString(), id.operation(), id.idempotencyKey());
    }

    static long codeLockId(String code) {
        return digest("identifier-scheme-code-lock.v1", code);
    }

    private static long digest(String... parts) {
        try (var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes)) {
            for (String part : parts) {
                byte[] encoded = part.getBytes(StandardCharsets.UTF_8);
                output.writeInt(encoded.length);
                output.write(encoded);
            }
            output.flush();
            return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())).getLong();
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Scheme lock identity encoding failed", failure);
        }
    }
}
