package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.port.LegalEntityRepository;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Optional;

/** Persists tenant-qualified legal-detail changes as one optimistic root/detail transaction. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactiveLegalEntityRepository implements LegalEntityRepository {

    private static final String PARAM_TENANT_ID = "tenantId";
    private static final String PARAM_PARTY_ID = "partyId";
    private static final String PARAM_PARTY_TYPE = "partyType";

    private final Mutiny.SessionFactory sessionFactory;
    private final LegalEntityPersistenceMapper mapper;

    @Inject
    public HibernateReactiveLegalEntityRepository(
            Mutiny.SessionFactory sessionFactory, LegalEntityPersistenceMapper mapper) {
        this.sessionFactory = sessionFactory;
        this.mapper = mapper;
    }

    @Override
    public Uni<Optional<LegalEntity>> findByTenantAndId(TenantId tenantId, PartyId partyId) {
        return translateUnexpected(sessionFactory.withSession(session -> findEntity(session, tenantId, partyId)
                .map(entity -> Optional.ofNullable(entity)
                        .map(value -> mapper.toDomain(value, value.legalEntityDetails())))));
    }

    @Override
    public Uni<LegalEntity> update(LegalEntity candidate, PartyVersion expectedVersion) {
        Uni<LegalEntity> operation = sessionFactory.withTransaction((session, transaction) ->
                updateRoot(session, candidate, expectedVersion)
                        .flatMap(rows -> rows == 1 ? updateDetails(session, candidate)
                                : resolveMissingOrMismatch(session, candidate.tenantId(), candidate.partyId(),
                                        expectedVersion))
                        .flatMap(rows -> rows == 1 ? Uni.createFrom().voidItem()
                                : Uni.createFrom().failure(new IllegalStateException("Legal details are missing")))
                        .invoke(session::clear)
                        .flatMap(ignored -> findEntity(session, candidate.tenantId(), candidate.partyId()))
                        .map(entity -> {
                            if (entity == null) {
                                throw new IllegalStateException("Updated legal entity cannot be reloaded");
                            }
                            return mapper.toDomain(entity, entity.legalEntityDetails());
                        }));
        return translateUnexpected(operation
                .onFailure(PersistenceExceptionTranslator::isOptimisticLock)
                .recoverWithUni(cause -> sessionFactory.withSession(session ->
                        findCurrentVersion(session, candidate.tenantId(), candidate.partyId()))
                        .flatMap(version -> Uni.createFrom().failure(version == null
                                ? new ApplicationException(new ApplicationFailure.LegalEntityNotFound(
                                        candidate.partyId(), candidate.tenantId()), cause)
                                : new ApplicationException(new ApplicationFailure.ExpectedVersionMismatch(
                                        expectedVersion, new PartyVersion(version)), cause)))));
    }

    private static Uni<Integer> updateRoot(
            Mutiny.Session session, LegalEntity candidate, PartyVersion expectedVersion) {
        // Explicit version arithmetic also protects identical and detail-only accepted writes.
        return session.createMutationQuery("""
                update PartyEntity party
                set party.displayName = :displayName,
                    party.updatedAt = :updatedAt,
                    party.updatedBy = :updatedBy,
                    party.version = party.version + 1
                where party.tenantId = :tenantId
                  and party.id = :partyId
                  and party.type = :partyType
                  and party.version = :expectedVersion
                """)
                .setParameter("displayName", candidate.displayName())
                .setParameter("updatedAt", candidate.auditInfo().updatedAt())
                .setParameter("updatedBy", candidate.auditInfo().updatedBy())
                .setParameter(PARAM_TENANT_ID, candidate.tenantId().value())
                .setParameter(PARAM_PARTY_ID, candidate.partyId().value())
                .setParameter(PARAM_PARTY_TYPE, PartyType.LEGAL_ENTITY)
                .setParameter("expectedVersion", expectedVersion.value())
                .executeUpdate();
    }

    private static Uni<Integer> updateDetails(Mutiny.Session session, LegalEntity candidate) {
        // The globally unique detail key is used only after the qualified root guard succeeds.
        return session.createMutationQuery("""
                update LegalEntityDetailsEntity details
                set details.legalName = :legalName,
                    details.tradeName = :tradeName,
                    details.legalFormCode = :legalFormCode,
                    details.incorporationCountryCode = :countryCode,
                    details.incorporatedOn = :incorporatedOn,
                    details.dissolvedOn = :dissolvedOn,
                    details.updatedAt = :updatedAt,
                    details.updatedBy = :updatedBy
                where details.partyId = :partyId
                """)
                .setParameter("legalName", candidate.details().legalName())
                .setParameter("tradeName", candidate.details().tradeName())
                .setParameter("legalFormCode", candidate.details().legalFormCode())
                .setParameter("countryCode", candidate.details().incorporationCountryCode())
                .setParameter("incorporatedOn", candidate.details().incorporatedOn())
                .setParameter("dissolvedOn", candidate.details().dissolvedOn())
                .setParameter("updatedAt", candidate.auditInfo().updatedAt())
                .setParameter("updatedBy", candidate.auditInfo().updatedBy())
                .setParameter(PARAM_PARTY_ID, candidate.partyId().value())
                .executeUpdate();
    }

    private static Uni<Integer> resolveMissingOrMismatch(
            Mutiny.Session session, TenantId tenantId, PartyId partyId, PartyVersion expectedVersion) {
        return findCurrentVersion(session, tenantId, partyId)
                .flatMap(version -> Uni.createFrom().failure(version == null
                        ? new ApplicationException(new ApplicationFailure.LegalEntityNotFound(partyId, tenantId))
                        : new ApplicationException(new ApplicationFailure.ExpectedVersionMismatch(
                                expectedVersion, new PartyVersion(version)))));
    }

    private static Uni<PartyEntity> findEntity(Mutiny.Session session, TenantId tenantId, PartyId partyId) {
        return session.createQuery("""
                select party from PartyEntity party
                join fetch party.legalEntityDetails details
                where party.tenantId = :tenantId
                  and party.id = :partyId
                  and party.type = :partyType
                """, PartyEntity.class)
                .setParameter(PARAM_TENANT_ID, tenantId.value())
                .setParameter(PARAM_PARTY_ID, partyId.value())
                .setParameter(PARAM_PARTY_TYPE, PartyType.LEGAL_ENTITY)
                .getSingleResultOrNull();
    }

    private static Uni<Long> findCurrentVersion(Mutiny.Session session, TenantId tenantId, PartyId partyId) {
        return session.createQuery("""
                select party.version from PartyEntity party
                where party.tenantId = :tenantId
                  and party.id = :partyId
                  and party.type = :partyType
                """, Long.class)
                .setParameter(PARAM_TENANT_ID, tenantId.value())
                .setParameter(PARAM_PARTY_ID, partyId.value())
                .setParameter(PARAM_PARTY_TYPE, PartyType.LEGAL_ENTITY)
                .getSingleResultOrNull();
    }

    private static <T> Uni<T> translateUnexpected(Uni<T> operation) {
        return operation.onFailure(PersistenceExceptionTranslator::requiresTranslation)
                .transform(PersistenceExceptionTranslator::toApplicationException);
    }
}
