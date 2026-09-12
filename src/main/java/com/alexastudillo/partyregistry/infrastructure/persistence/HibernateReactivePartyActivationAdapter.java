package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyActivatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyActivationCandidate;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyActivationPort;
import com.alexastudillo.partyregistry.application.support.UuidV7;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.policy.PartyIdentifierEvidence;
import com.alexastudillo.partyregistry.infrastructure.configuration.PartyOutboxPersistenceMode;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.LockMode;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.List;
import java.util.Objects;

/**
 * Activates tenant-owned Parties and stores enabled activation events in one reactive transaction.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactivePartyActivationAdapter implements PartyActivationPort {

    private static final String PARAM_TENANT_ID = "tenantId";
    private static final String PARAM_PARTY_ID = "partyId";

    private final Mutiny.SessionFactory sessionFactory;
    private final NaturalPersonPersistenceMapper naturalPersonMapper;
    private final LegalEntityPersistenceMapper legalEntityMapper;
    private final PartyIdentifierPersistenceMapper identifierMapper;
    private final IdentifierSchemePersistenceMapper schemeMapper;
    private final PartyOutboxEventPersistenceMapper outboxMapper;
    private final PartyActivationDecision decision;
    private final PartyOutboxPersistenceMode outboxMode;
    private final OperationObservationPort observationPort;

    /**
     * Creates the activation adapter from its same-session persistence collaborators.
     */
    @Inject
    public HibernateReactivePartyActivationAdapter(
            Mutiny.SessionFactory sessionFactory,
            NaturalPersonPersistenceMapper naturalPersonMapper,
            LegalEntityPersistenceMapper legalEntityMapper,
            PartyIdentifierPersistenceMapper identifierMapper,
            IdentifierSchemePersistenceMapper schemeMapper,
            PartyOutboxEventPersistenceMapper outboxMapper,
            OperationObservationPort observationPort,
            @ConfigProperty(name = "party-registry.outbox.mode") String outboxMode) {
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.naturalPersonMapper = Objects.requireNonNull(naturalPersonMapper, "naturalPersonMapper");
        this.legalEntityMapper = Objects.requireNonNull(legalEntityMapper, "legalEntityMapper");
        this.identifierMapper = Objects.requireNonNull(identifierMapper, "identifierMapper");
        this.schemeMapper = Objects.requireNonNull(schemeMapper, "schemeMapper");
        this.outboxMapper = Objects.requireNonNull(outboxMapper, "outboxMapper");
        this.decision = new PartyActivationDecision();
        this.outboxMode = PartyOutboxPersistenceMode.fromConfiguration(outboxMode);
        this.observationPort = Objects.requireNonNull(observationPort, "observationPort");
    }

    @Override
    public Uni<PartyDetailsResult> activate(PartyActivationCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return OperationObservation.observe(
                candidate.requestMetadata(),
                ObservedOperation.TRANSACTION_PARTY_ACTIVATION,
                observationPort,
                () -> activateTransaction(candidate),
                ignored -> OperationOutcome.ACTIVATED);
    }

    private Uni<PartyDetailsResult> activateTransaction(PartyActivationCandidate candidate) {
        Uni<PartyDetailsResult> operation = Uni.createFrom().deferred(() -> sessionFactory
                .withTransaction((session, transaction) -> lockParty(session, candidate)
                        .flatMap(entity -> activateLocked(session, candidate, entity))));

        return operation
                .onFailure(failure -> PersistenceExceptionTranslator.requiresTranslation(failure)
                        && PersistenceExceptionTranslator.isOptimisticLock(failure))
                .recoverWithUni(failure -> recoverOptimisticRace(candidate, failure))
                .onFailure()
                .transform(HibernateReactivePartyActivationAdapter::translateFailure);
    }

    private static Uni<PartyEntity> lockParty(
            Mutiny.Session session,
            PartyActivationCandidate candidate) {
        return session.createQuery("""
                from PartyEntity party
                where party.tenantId = :tenantId
                  and party.id = :partyId
                """, PartyEntity.class)
                .setParameter(PARAM_TENANT_ID, candidate.requestMetadata().tenantId().value())
                .setParameter(PARAM_PARTY_ID, candidate.partyId().value())
                .setLockMode(LockMode.PESSIMISTIC_WRITE)
                .getSingleResultOrNull();
    }

    private Uni<PartyDetailsResult> activateLocked(
            Mutiny.Session session,
            PartyActivationCandidate candidate,
            PartyEntity entity) {
        decision.requireLockedState(
                candidate,
                entity == null ? null : new PartyVersion(entity.version()),
                entity == null ? null : entity.recordStatus());
        return loadParty(session, candidate)
                .flatMap(party -> loadEvidence(session, candidate)
                        .map(evidence -> decision.activate(candidate, party, evidence)))
                .flatMap(activated -> persistActivation(session, candidate, entity, activated));
    }

    private Uni<Party> loadParty(
            Mutiny.Session session,
            PartyActivationCandidate candidate) {
        return session.createQuery("""
                select party, naturalDetails, legalDetails
                from PartyEntity party
                left join party.naturalPersonDetails naturalDetails
                left join party.legalEntityDetails legalDetails
                where party.tenantId = :tenantId
                  and party.id = :partyId
                """, Object[].class)
                .setParameter(PARAM_TENANT_ID, candidate.requestMetadata().tenantId().value())
                .setParameter(PARAM_PARTY_ID, candidate.partyId().value())
                .getSingleResultOrNull()
                .flatMap(row -> row == null
                        ? Uni.createFrom().failure(PersistenceExceptionTranslator.toApplicationException(
                                new IllegalStateException("Locked Party cannot be reloaded")))
                        : Uni.createFrom().item(toDomain(row)));
    }

    private Party toDomain(Object[] row) {
        PartyEntity entity = (PartyEntity) row[0];
        NaturalPersonDetailsEntity naturalDetails = (NaturalPersonDetailsEntity) row[1];
        LegalEntityDetailsEntity legalDetails = (LegalEntityDetailsEntity) row[2];
        if (naturalDetails != null && legalDetails != null) {
            throw PersistenceExceptionTranslator.toApplicationException(
                    new IllegalStateException("Party has multiple detail rows"));
        }
        return switch (entity.type()) {
            case NATURAL_PERSON -> naturalPersonMapper.toDomain(entity, naturalDetails);
            case LEGAL_ENTITY -> legalEntityMapper.toDomain(entity, legalDetails);
        };
    }

    private Uni<List<PartyIdentifierEvidence>> loadEvidence(
            Mutiny.Session session,
            PartyActivationCandidate candidate) {
        return session.createQuery("""
                select identifier, scheme
                from PartyIdentifierEntity identifier
                join IdentifierSchemeEntity scheme
                  on scheme.id = identifier.identifierSchemeId
                where identifier.tenantId = :tenantId
                  and identifier.partyId = :partyId
                """, Object[].class)
                .setParameter(PARAM_TENANT_ID, candidate.requestMetadata().tenantId().value())
                .setParameter(PARAM_PARTY_ID, candidate.partyId().value())
                .getResultList()
                .map(rows -> rows.stream()
                        .map(row -> new PartyIdentifierEvidence(
                                identifierMapper.toDomain((PartyIdentifierEntity) row[0]),
                                schemeMapper.toDomain((IdentifierSchemeEntity) row[1])))
                        .toList());
    }

    private Uni<PartyDetailsResult> persistActivation(
            Mutiny.Session session,
            PartyActivationCandidate candidate,
            PartyEntity entity,
            Party activated) {
        entity.applyActivation(activated);
        Uni<Void> outboxWrite = outboxMode.storesEvents()
                ? session.persist(outboxMapper.toEntity(activationEvent(candidate, activated)))
                        .replaceWithVoid()
                : Uni.createFrom().voidItem();
        return outboxWrite
                .call(session::flush)
                .invoke(() -> requireIncrementedVersion(entity, activated))
                .replaceWith(PartyDetailsResult.fromAggregate(activated));
    }

    static PartyActivatedOutboxCandidate activationEvent(
            PartyActivationCandidate candidate,
            Party activated) {
        return new PartyActivatedOutboxCandidate(
                UuidV7.generate(candidate.occurredAt()),
                activated.tenantId(),
                activated.partyId(),
                activated.version(),
                activated.type(),
                activated.recordStatus(),
                candidate.occurredAt(),
                candidate.requestMetadata().processId(),
                candidate.requestMetadata().userId());
    }

    private static void requireIncrementedVersion(PartyEntity entity, Party activated) {
        if (entity.version() != activated.version().value()) {
            throw PersistenceExceptionTranslator.toApplicationException(
                    new IllegalStateException("Hibernate did not increment the Party version exactly once"));
        }
    }

    private Uni<PartyDetailsResult> recoverOptimisticRace(
            PartyActivationCandidate candidate,
            Throwable cause) {
        return sessionFactory.withSession(session -> findCurrentVersion(session, candidate))
                .flatMap(currentVersion -> currentVersion == null
                        ? Uni.createFrom().failure(new ApplicationException(
                                new ApplicationFailure.PartyNotFound(
                                        candidate.partyId(),
                                        candidate.requestMetadata().tenantId()),
                                cause))
                        : Uni.createFrom().failure(new ApplicationException(
                                new ApplicationFailure.StalePartyVersion(
                                        candidate.expectedVersion(),
                                        new PartyVersion(currentVersion)),
                                cause)));
    }

    private static Uni<Long> findCurrentVersion(
            Mutiny.Session session,
            PartyActivationCandidate candidate) {
        return session.createQuery("""
                select party.version
                from PartyEntity party
                where party.tenantId = :tenantId
                  and party.id = :partyId
                """, Long.class)
                .setParameter(PARAM_TENANT_ID, candidate.requestMetadata().tenantId().value())
                .setParameter(PARAM_PARTY_ID, candidate.partyId().value())
                .getSingleResultOrNull();
    }

    static Throwable translateFailure(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        return PersistenceExceptionTranslator.requiresTranslation(failure)
                ? PersistenceExceptionTranslator.toApplicationException(failure)
                : failure;
    }
}
