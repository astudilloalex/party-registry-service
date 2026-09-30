package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import com.alexastudillo.partyregistry.application.port.RegistrationFingerprintPort;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Objects;
import java.util.Optional;

/**
 * Provides reusable version-two idempotency storage and lookup operations for
 * registration.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyRegistrationIdempotencyPersistence {

    private final Mutiny.SessionFactory sessionFactory;
    private final RegistrationFingerprintPort fingerprintPort;
    private final IdempotencyResultSnapshotCodec snapshotCodec;

    /**
     * Creates the persistence helper from reactive session, fingerprint, and
     * snapshot services.
     */
    @Inject
    public PartyRegistrationIdempotencyPersistence(
            Mutiny.SessionFactory sessionFactory,
            RegistrationFingerprintPort fingerprintPort,
            IdempotencyResultSnapshotCodec snapshotCodec) {
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.fingerprintPort = Objects.requireNonNull(fingerprintPort, "fingerprintPort");
        this.snapshotCodec = Objects.requireNonNull(snapshotCodec, "snapshotCodec");
    }

    /**
     * Loads a completed current-operation registration and validates its
     * fingerprint.
     */
    Uni<Optional<PartyRegistrationResult>> findCompleted(
            TenantId tenantId,
            String operation,
            String idempotencyKey,
            String registrationFingerprint) {
        ApiIdempotencyRecordId id = id(tenantId, operation, idempotencyKey);
        Uni<Optional<PartyRegistrationResult>> lookup = sessionFactory.withSession(session -> session
                .find(ApiIdempotencyRecordEntity.class, id)
                .map(rec -> Optional.ofNullable(rec)
                        .map(value -> resolve(value, idempotencyKey, registrationFingerprint))));
        return translateUnexpected(lookup);
    }

    /**
     * Checks an operation key by existence without loading or decoding its
     * snapshot.
     */
    Uni<Boolean> hasCompletedKey(
            TenantId tenantId,
            String operation,
            String idempotencyKey) {
        ApiIdempotencyRecordId id = id(tenantId, operation, idempotencyKey);
        Uni<Boolean> lookup = sessionFactory.withSession(session -> session.createQuery("""
                select count(record)
                from ApiIdempotencyRecordEntity record
                where record.id = :id
                """, Long.class)
                .setParameter("id", id)
                .getSingleResult()
                .map(count -> count > 0));
        return translateUnexpected(lookup);
    }

    /**
     * Creates a completed idempotency entity containing only a safe version-two
     * result.
     */
    ApiIdempotencyRecordEntity newCompletedRecord(
            String operation,
            String idempotencyKey,
            String registrationFingerprint,
            PartyRegistrationResult result) {
        Objects.requireNonNull(result, "result");
        IdempotencyResultSnapshot snapshot = snapshotCodec.encodeRegistration(result);
        return new ApiIdempotencyRecordEntity(
                id(result.party().tenantId(), operation, idempotencyKey),
                registrationFingerprint,
                result.party().partyId().value(),
                snapshot,
                result.party().createdAt(),
                result.party().createdBy());
    }

    private PartyRegistrationResult resolve(
            ApiIdempotencyRecordEntity recordEntity,
            String idempotencyKey,
            String registrationFingerprint) {
        if (!fingerprintPort.matches(recordEntity.requestHash(), registrationFingerprint)) {
            throw new ApplicationException(
                    new ApplicationFailure.IdempotencyKeyConflict(idempotencyKey));
        }
        return snapshotCodec.decodeRegistration(recordEntity.resultSnapshot());
    }

    private static ApiIdempotencyRecordId id(
            TenantId tenantId,
            String operation,
            String idempotencyKey) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("Operation is required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        return new ApiIdempotencyRecordId(tenantId.value(), operation, idempotencyKey);
    }

    private static <T> Uni<T> translateUnexpected(Uni<T> operation) {
        return operation
                .onFailure(PersistenceExceptionTranslator::requiresTranslation)
                .transform(PersistenceExceptionTranslator::toApplicationException);
    }
}
