package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.CreateNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.IdempotentCreationOutcome;
import com.alexastudillo.partyregistry.application.model.IdempotentCreationResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.port.IdempotentNaturalPersonCreationPort;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Objects;
import java.util.Optional;

/**
 * Atomically persists natural-person creations and recovers idempotency races.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactiveIdempotentNaturalPersonCreationAdapter
        implements IdempotentNaturalPersonCreationPort {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String IDEMPOTENCY_PRIMARY_KEY = "pk_api_idempotency_records";

    private final Mutiny.SessionFactory sessionFactory;
    private final NaturalPersonPersistenceMapper mapper;

    @Inject
    public HibernateReactiveIdempotentNaturalPersonCreationAdapter(
            Mutiny.SessionFactory sessionFactory,
            NaturalPersonPersistenceMapper mapper) {
        this.sessionFactory = sessionFactory;
        this.mapper = mapper;
    }

    @Override
    public Uni<Optional<IdempotentCreationResult>> findCompleted(
            TenantId tenantId,
            String idempotencyKey,
            String requestHash) {
        Uni<Optional<IdempotentCreationResult>> operation = sessionFactory.withSession(session -> findRecord(
                session, tenantId, idempotencyKey)
                .map(idempotencyRecord -> Optional.ofNullable(idempotencyRecord)
                        .map(value -> resolve(value, idempotencyKey, requestHash))));
        return translateUnexpected(operation);
    }

    @Override
    public Uni<IdempotentCreationResult> createIdempotently(
            TenantId tenantId,
            String idempotencyKey,
            String requestHash,
            NaturalPerson naturalPerson) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(naturalPerson, "naturalPerson");
        if (!tenantId.equals(naturalPerson.tenantId())) {
            return Uni.createFrom().failure(PersistenceExceptionTranslator.toApplicationException(
                    new IllegalArgumentException(
                            "Creation tenant does not match aggregate tenant")));
        }

        NaturalPersonResult result = NaturalPersonResult.fromAggregate(naturalPerson);
        Uni<IdempotentCreationResult> operation = sessionFactory.withTransaction((session, transaction) -> {
            PartyEntity party = mapper.toEntity(naturalPerson);
            ApiIdempotencyRecordEntity idempotencyRecord = newRecord(
                    tenantId,
                    idempotencyKey,
                    requestHash,
                    result);
            return session.persist(party)
                    .call(session::flush)
                    .call(() -> session.persist(idempotencyRecord))
                    .call(session::flush)
                    .replaceWith(new IdempotentCreationResult(
                            result,
                            IdempotentCreationOutcome.CREATED));
        });

        return operation
                .onFailure(failure -> PersistenceExceptionTranslator.isConstraint(
                        failure,
                        UNIQUE_VIOLATION,
                        IDEMPOTENCY_PRIMARY_KEY))
                .recoverWithUni(failure -> recoverWinner(
                        tenantId,
                        idempotencyKey,
                        requestHash,
                        failure))
                .plug(this::translateUnexpected);
    }

    private Uni<IdempotentCreationResult> recoverWinner(
            TenantId tenantId,
            String idempotencyKey,
            String requestHash,
            Throwable cause) {
        return sessionFactory.withSession(session -> findRecord(session, tenantId, idempotencyKey))
                .flatMap(idempotencyRecord -> idempotencyRecord == null
                        ? Uni.createFrom()
                                .failure(PersistenceExceptionTranslator
                                        .toApplicationException(cause))
                        : Uni.createFrom().item(resolve(idempotencyRecord, idempotencyKey,
                                requestHash)));
    }

    private static IdempotentCreationResult resolve(
            ApiIdempotencyRecordEntity idempotencyRecord,
            String idempotencyKey,
            String requestHash) {
        if (!idempotencyRecord.requestHash().equals(requestHash)) {
            throw new ApplicationException(
                    new ApplicationFailure.IdempotencyKeyConflict(idempotencyKey));
        }
        if (idempotencyRecord
                .resultSnapshotSchemaVersion() != NaturalPersonResultSnapshot.CURRENT_SCHEMA_VERSION
                || idempotencyRecord.resultSnapshot().schemaVersion() != idempotencyRecord
                        .resultSnapshotSchemaVersion()) {
            throw new IllegalStateException("Idempotency snapshot schema versions do not match");
        }
        return new IdempotentCreationResult(
                idempotencyRecord.resultSnapshot().toResult(),
                IdempotentCreationOutcome.REPLAYED);
    }

    private static ApiIdempotencyRecordEntity newRecord(
            TenantId tenantId,
            String idempotencyKey,
            String requestHash,
            NaturalPersonResult result) {
        NaturalPersonResultSnapshot snapshot = NaturalPersonResultSnapshot.from(result);
        return new ApiIdempotencyRecordEntity(
                new ApiIdempotencyRecordId(
                        tenantId.value(),
                        CreateNaturalPersonCommand.OPERATION,
                        idempotencyKey),
                requestHash,
                result.partyId().value(),
                snapshot.schemaVersion(),
                snapshot,
                result.createdAt(),
                result.createdBy());
    }

    private static Uni<ApiIdempotencyRecordEntity> findRecord(
            Mutiny.Session session,
            TenantId tenantId,
            String idempotencyKey) {
        return session.find(
                ApiIdempotencyRecordEntity.class,
                new ApiIdempotencyRecordId(
                        tenantId.value(),
                        CreateNaturalPersonCommand.OPERATION,
                        idempotencyKey));
    }

    private <T> Uni<T> translateUnexpected(Uni<T> operation) {
        return operation
                .onFailure(PersistenceExceptionTranslator::requiresTranslation)
                .transform(PersistenceExceptionTranslator::toApplicationException);
    }
}
