package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.port.NaturalPersonRepository;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
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

/**
 * Persists tenant-scoped natural-person aggregates with Hibernate Reactive.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactiveNaturalPersonRepository implements NaturalPersonRepository {

    private static final String FIND_NATURAL_PERSON = """
            select party
            from PartyEntity party
            join fetch party.naturalPersonDetails details
            where party.tenantId = :tenantId
              and party.id = :partyId
              and party.type = :partyType
            """;
    private static final String PARAM_TENANT_ID = "tenantId";
    private static final String PARAM_PARTY_ID = "partyId";
    private static final String PARAM_PARTY_TYPE = "partyType";

    private final Mutiny.SessionFactory sessionFactory;
    private final NaturalPersonPersistenceMapper mapper;

    @Inject
    public HibernateReactiveNaturalPersonRepository(
            Mutiny.SessionFactory sessionFactory,
            NaturalPersonPersistenceMapper mapper) {
        this.sessionFactory = sessionFactory;
        this.mapper = mapper;
    }

    @Override
    public Uni<Optional<NaturalPerson>> findByTenantAndId(TenantId tenantId, PartyId partyId) {
        Uni<Optional<NaturalPerson>> operation = sessionFactory
                .withSession(session -> findEntity(session, tenantId, partyId)
                        .map(entity -> Optional.ofNullable(entity).map(mapper::toDomain)));
        return translateUnexpected(operation);
    }

    @Override
    public Uni<NaturalPerson> update(NaturalPerson updatedPerson, PartyVersion expectedVersion) {
        Uni<NaturalPerson> operation = sessionFactory.withTransaction((session, transaction) -> updateParty(
                session, updatedPerson, expectedVersion)
                .flatMap(updatedRows -> updatedRows == 1
                        ? updateDetails(session, updatedPerson)
                        : resolveMissingOrMismatch(
                                session,
                                updatedPerson.tenantId(),
                                updatedPerson.partyId(),
                                expectedVersion))
                .flatMap(updatedRows -> updatedRows == 1
                        ? Uni.createFrom().voidItem()
                        : Uni.createFrom().failure(
                                PersistenceExceptionTranslator.toApplicationException(
                                        new IllegalStateException(
                                                "Natural-person details are missing"))))
                .invoke(session::clear)
                .flatMap(ignored -> findEntity(
                        session,
                        updatedPerson.tenantId(),
                        updatedPerson.partyId()))
                .flatMap(entity -> entity == null
                        ? Uni.createFrom().failure(
                                PersistenceExceptionTranslator.toApplicationException(
                                        new IllegalStateException(
                                                "Updated natural person cannot be reloaded")))
                        : Uni.createFrom().item(mapper.toDomain(entity))));

        return operation
                .onFailure(PersistenceExceptionTranslator::isOptimisticLock)
                .recoverWithUni(failure -> recoverOptimisticFailure(
                        updatedPerson.tenantId(),
                        updatedPerson.partyId(),
                        expectedVersion,
                        failure))
                .plug(this::translateUnexpected);
    }

    private static Uni<Integer> updateParty(
            Mutiny.Session session,
            NaturalPerson updatedPerson,
            PartyVersion expectedVersion) {
        // Always touch the root so detail-only changes advance the aggregate version.
        return session.createMutationQuery("""
                update PartyEntity party
                set party.displayName = :displayName,
                    party.recordStatus = :recordStatus,
                    party.updatedAt = :updatedAt,
                    party.updatedBy = :updatedBy,
                    party.version = party.version + 1
                where party.tenantId = :tenantId
                  and party.id = :partyId
                  and party.type = :partyType
                  and party.version = :expectedVersion
                """)
                .setParameter("displayName", updatedPerson.displayName())
                .setParameter("recordStatus", updatedPerson.recordStatus())
                .setParameter("updatedAt", updatedPerson.auditInfo().updatedAt())
                .setParameter("updatedBy", updatedPerson.auditInfo().updatedBy())
                .setParameter(PARAM_TENANT_ID, updatedPerson.tenantId().value())
                .setParameter(PARAM_PARTY_ID, updatedPerson.partyId().value())
                .setParameter(PARAM_PARTY_TYPE, PartyType.NATURAL_PERSON)
                .setParameter("expectedVersion", expectedVersion.value())
                .executeUpdate();
    }

    private static Uni<Integer> updateDetails(
            Mutiny.Session session,
            NaturalPerson updatedPerson) {
        return session.createMutationQuery("""
                update NaturalPersonDetailsEntity details
                set details.givenNames = :givenNames,
                    details.familyNames = :familyNames,
                    details.preferredName = :preferredName,
                    details.birthDate = :birthDate,
                    details.dateOfDeath = :dateOfDeath,
                    details.birthCountryCode = :birthCountryCode,
                    details.updatedAt = :updatedAt,
                    details.updatedBy = :updatedBy
                where details.partyId = :partyId
                """)
                .setParameter("givenNames", updatedPerson.details().givenNames())
                .setParameter("familyNames", updatedPerson.details().familyNames())
                .setParameter("preferredName", updatedPerson.details().preferredName())
                .setParameter("birthDate", updatedPerson.details().birthDate())
                .setParameter("dateOfDeath", updatedPerson.details().dateOfDeath())
                .setParameter("birthCountryCode", updatedPerson.details().birthCountryCode())
                .setParameter("updatedAt", updatedPerson.auditInfo().updatedAt())
                .setParameter("updatedBy", updatedPerson.auditInfo().updatedBy())
                .setParameter(PARAM_PARTY_ID, updatedPerson.partyId().value())
                .executeUpdate();
    }

    private static Uni<Integer> resolveMissingOrMismatch(
            Mutiny.Session session,
            TenantId tenantId,
            PartyId partyId,
            PartyVersion expectedVersion) {
        return findCurrentVersion(session, tenantId, partyId)
                .flatMap(currentVersion -> currentVersion == null
                        ? Uni.createFrom().failure(new ApplicationException(
                                new ApplicationFailure.NaturalPersonNotFound(partyId,
                                        tenantId)))
                        : Uni.createFrom().failure(versionMismatch(
                                expectedVersion,
                                new PartyVersion(currentVersion))));
    }

    private Uni<NaturalPerson> recoverOptimisticFailure(
            TenantId tenantId,
            PartyId partyId,
            PartyVersion expectedVersion,
            Throwable cause) {
        return sessionFactory.withSession(session -> findCurrentVersion(session, tenantId, partyId))
                .flatMap(currentVersion -> currentVersion == null
                        ? Uni.createFrom().failure(new ApplicationException(
                                new ApplicationFailure.NaturalPersonNotFound(partyId,
                                        tenantId),
                                cause))
                        : Uni.createFrom().failure(new ApplicationException(
                                new ApplicationFailure.ExpectedVersionMismatch(
                                        expectedVersion,
                                        new PartyVersion(currentVersion)),
                                cause)));
    }

    private static ApplicationException versionMismatch(
            PartyVersion expectedVersion,
            PartyVersion currentVersion) {
        return new ApplicationException(new ApplicationFailure.ExpectedVersionMismatch(
                expectedVersion,
                currentVersion));
    }

    private static Uni<PartyEntity> findEntity(
            Mutiny.Session session,
            TenantId tenantId,
            PartyId partyId) {
        return session.createQuery(FIND_NATURAL_PERSON, PartyEntity.class)
                .setParameter(PARAM_TENANT_ID, tenantId.value())
                .setParameter(PARAM_PARTY_ID, partyId.value())
                .setParameter(PARAM_PARTY_TYPE, PartyType.NATURAL_PERSON)
                .getSingleResultOrNull();
    }

    private static Uni<Long> findCurrentVersion(
            Mutiny.Session session,
            TenantId tenantId,
            PartyId partyId) {
        return session.createQuery("""
                select party.version
                from PartyEntity party
                where party.tenantId = :tenantId
                  and party.id = :partyId
                  and party.type = :partyType
                """, Long.class)
                .setParameter(PARAM_TENANT_ID, tenantId.value())
                .setParameter(PARAM_PARTY_ID, partyId.value())
                .setParameter(PARAM_PARTY_TYPE, PartyType.NATURAL_PERSON)
                .getSingleResultOrNull();
    }

    private <T> Uni<T> translateUnexpected(Uni<T> operation) {
        return operation
                .onFailure(PersistenceExceptionTranslator::requiresTranslation)
                .transform(PersistenceExceptionTranslator::toApplicationException);
    }
}
