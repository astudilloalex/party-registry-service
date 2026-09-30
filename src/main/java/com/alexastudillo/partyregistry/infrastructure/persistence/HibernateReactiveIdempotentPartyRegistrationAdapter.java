package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.LegalEntityRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.LegalEntityResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import com.alexastudillo.partyregistry.application.port.IdempotentPartyRegistrationPort;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSchemePolicy;
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
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Atomically registers Party and PartyIdentifier aggregates with deterministic race recovery.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactiveIdempotentPartyRegistrationAdapter
        implements IdempotentPartyRegistrationPort {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String IDEMPOTENCY_PRIMARY_KEY = "pk_api_idempotency_records";
    private static final String ACTIVE_IDENTIFIER_UNIQUE_CONSTRAINT =
            "uq_party_identifiers_active_value";

    private final Mutiny.SessionFactory sessionFactory;
    private final NaturalPersonPersistenceMapper naturalPersonMapper;
    private final LegalEntityPersistenceMapper legalEntityMapper;
    private final IdentifierSchemePersistenceMapper identifierSchemeMapper;
    private final PartyIdentifierPersistenceMapper identifierMapper;
    private final PartyOutboxEventPersistenceMapper outboxMapper;
    private final PartyRegistrationIdempotencyPersistence idempotencyPersistence;
    private final IdentifierSchemePolicy identifierSchemePolicy;
    private final PartyRegistrationWriteSequence writeSequence;
    private final PartyOutboxPersistenceMode outboxMode;
    private final OperationObservationPort observationPort;

    /**
     * Creates the registration adapter from its reactive persistence collaborators.
     */
    @Inject
    public HibernateReactiveIdempotentPartyRegistrationAdapter(
            Mutiny.SessionFactory sessionFactory,
            NaturalPersonPersistenceMapper naturalPersonMapper,
            LegalEntityPersistenceMapper legalEntityMapper,
            IdentifierSchemePersistenceMapper identifierSchemeMapper,
            PartyIdentifierPersistenceMapper identifierMapper,
            PartyOutboxEventPersistenceMapper outboxMapper,
            PartyRegistrationIdempotencyPersistence idempotencyPersistence,
            OperationObservationPort observationPort,
            @ConfigProperty(name = "party-registry.outbox.mode") String outboxMode) {
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.naturalPersonMapper = Objects.requireNonNull(naturalPersonMapper, "naturalPersonMapper");
        this.legalEntityMapper = Objects.requireNonNull(legalEntityMapper, "legalEntityMapper");
        this.identifierSchemeMapper = Objects.requireNonNull(identifierSchemeMapper, "identifierSchemeMapper");
        this.identifierMapper = Objects.requireNonNull(identifierMapper, "identifierMapper");
        this.outboxMapper = Objects.requireNonNull(outboxMapper, "outboxMapper");
        this.idempotencyPersistence = Objects.requireNonNull(
                idempotencyPersistence,
                "idempotencyPersistence");
        this.identifierSchemePolicy = new IdentifierSchemePolicy();
        this.writeSequence = new PartyRegistrationWriteSequence();
        this.outboxMode = PartyOutboxPersistenceMode.fromConfiguration(outboxMode);
        this.observationPort = Objects.requireNonNull(observationPort, "observationPort");
    }

    @Override
    public Uni<Optional<PartyRegistrationResult>> findCompleted(
            TenantId tenantId,
            String operation,
            String idempotencyKey,
            String registrationFingerprint) {
        return idempotencyPersistence.findCompleted(
                tenantId,
                operation,
                idempotencyKey,
                registrationFingerprint);
    }

    @Override
    public Uni<Boolean> hasCompletedKey(
            TenantId tenantId,
            String operation,
            String idempotencyKey) {
        return idempotencyPersistence.hasCompletedKey(tenantId, operation, idempotencyKey);
    }

    @Override
    public Uni<PartyRegistrationResult> registerNaturalPerson(
            NaturalPersonRegistrationCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return OperationObservation.observe(
                candidate.requestMetadata(),
                ObservedOperation.TRANSACTION_NATURAL_PERSON_REGISTRATION,
                observationPort,
                () -> register(
                        candidate,
                        () -> naturalPersonMapper.toEntity(candidate.party()),
                        () -> NaturalPersonResult.fromAggregate(candidate.party())),
                OperationObservation::registrationOutcome);
    }

    @Override
    public Uni<PartyRegistrationResult> registerLegalEntity(
            LegalEntityRegistrationCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return OperationObservation.observe(
                candidate.requestMetadata(),
                ObservedOperation.TRANSACTION_LEGAL_ENTITY_REGISTRATION,
                observationPort,
                () -> register(
                        candidate,
                        () -> legalEntityMapper.toEntity(candidate.party()),
                        () -> LegalEntityResult.fromAggregate(candidate.party())),
                OperationObservation::registrationOutcome);
    }

    private Uni<PartyRegistrationResult> register(
            PartyRegistrationCandidate candidate,
            Supplier<PartyEntity> partyEntityFactory,
            Supplier<PartyDetailsResult> partyResultFactory) {
        Uni<PartyRegistrationResult> operation = Uni.createFrom().deferred(() -> {
            PartyRegistrationResult createdResult = new PartyRegistrationResult(
                    partyResultFactory.get(),
                    PartyIdentifierResult.fromAggregate(
                            candidate.initialIdentifier(),
                            candidate.identifierScheme()),
                    PartyRegistrationOutcome.CREATED);
            ApiIdempotencyRecordEntity idempotencyRecord = idempotencyPersistence.newCompletedRecord(
                    candidate.operation(),
                    candidate.idempotencyKey(),
                    candidate.registrationFingerprint(),
                    createdResult);
            PartyEntity partyEntity = partyEntityFactory.get();
            PartyIdentifierEntity identifierEntity = identifierMapper.toEntity(
                    candidate.initialIdentifier());
            List<PartyOutboxEventEntity> outboxEntities = outboxMode.storesEvents()
                    ? candidate.outboxCandidates().stream().map(outboxMapper::toEntity).toList()
                    : List.of();

            return sessionFactory.withTransaction((session, transaction) -> recheckIdentifierScheme(
                    session,
                    candidate)
                    .flatMap(ignored -> persistRegistration(
                            session,
                            partyEntity,
                            idempotencyRecord,
                            identifierEntity,
                            outboxEntities))
                    .replaceWith(createdResult));
        });

        return operation
                .onFailure(failure -> PersistenceExceptionTranslator.requiresTranslation(failure)
                        && PersistenceExceptionTranslator.isConstraint(
                                failure,
                                UNIQUE_VIOLATION,
                                IDEMPOTENCY_PRIMARY_KEY))
                .recoverWithUni(failure -> recoverCommittedWinner(candidate, failure))
                .onFailure(failure -> PersistenceExceptionTranslator.requiresTranslation(failure)
                        && PersistenceExceptionTranslator.isConstraint(
                                failure,
                                UNIQUE_VIOLATION,
                                ACTIVE_IDENTIFIER_UNIQUE_CONSTRAINT))
                .transform(failure -> new ApplicationException(
                        new ApplicationFailure.IdentifierUniquenessConflict(
                                candidate.identifierScheme().id()),
                        failure))
                .onFailure(PersistenceExceptionTranslator::requiresTranslation)
                .transform(PersistenceExceptionTranslator::toApplicationException);
    }

    private Uni<Void> recheckIdentifierScheme(
            Mutiny.Session session,
            PartyRegistrationCandidate candidate) {
        IdentifierScheme expected = candidate.identifierScheme();
        return session.find(
                IdentifierSchemeEntity.class,
                expected.id().value(),
                LockMode.PESSIMISTIC_READ)
                .invoke(entity -> requireEligibleUnchangedScheme(entity, expected, candidate))
                .replaceWithVoid();
    }

    private void requireEligibleUnchangedScheme(
            IdentifierSchemeEntity entity,
            IdentifierScheme expected,
            PartyRegistrationCandidate candidate) {
        if (entity == null) {
            throw unavailableScheme(expected);
        }
        IdentifierScheme current = identifierSchemeMapper.toDomain(entity);
        if (!hasExactStableIdentity(current, expected)
                || !current.version().equals(expected.version())) {
            throw unavailableScheme(expected);
        }
        try {
            identifierSchemePolicy.requireRegistrationEligibility(current, candidate.party().type());
        } catch (DomainValidationException exception) {
            throw switch (exception.violation()) {
                case DomainViolation.IDENTIFIER_SCHEME_INACTIVE -> new ApplicationException(
                        new ApplicationFailure.InactiveIdentifierScheme(expected.id()),
                        exception);
                case DomainViolation.IDENTIFIER_SCHEME_INCOMPATIBLE -> new ApplicationException(
                        new ApplicationFailure.IncompatibleIdentifierScheme(
                                expected.id(),
                                candidate.party().type()),
                        exception);
                default -> PersistenceExceptionTranslator.toApplicationException(exception);
            };
        }
    }

    private Uni<Void> persistRegistration(
            Mutiny.Session session,
            PartyEntity party,
            ApiIdempotencyRecordEntity idempotencyRecord,
            PartyIdentifierEntity identifier,
            List<PartyOutboxEventEntity> outboxEvents) {
        List<Supplier<Uni<?>>> outboxWrites = outboxEvents.stream()
                .<Supplier<Uni<?>>>map(event -> () -> session.persist(event))
                .toList();
        return writeSequence.execute(
                () -> session.persist(party),
                session::flush,
                () -> session.persist(idempotencyRecord),
                session::flush,
                () -> session.persist(identifier),
                outboxWrites,
                session::flush);
    }

    private Uni<PartyRegistrationResult> recoverCommittedWinner(
            PartyRegistrationCandidate candidate,
            Throwable raceFailure) {
        return idempotencyPersistence.findCompleted(
                candidate.party().tenantId(),
                candidate.operation(),
                candidate.idempotencyKey(),
                candidate.registrationFingerprint())
                .flatMap(completed -> completed
                        .map(result -> Uni.createFrom().item(asReplay(result)))
                        .orElseGet(() -> Uni.createFrom().failure(
                                PersistenceExceptionTranslator.toApplicationException(raceFailure))));
    }

    private static boolean hasExactStableIdentity(
            IdentifierScheme current,
            IdentifierScheme expected) {
        return current.id().equals(expected.id())
                && current.code().equals(expected.code())
                && current.issuingCountryCode().equals(expected.issuingCountryCode())
                && current.category() == expected.category()
                && current.applicableSubjectType() == expected.applicableSubjectType();
    }

    private static ApplicationException unavailableScheme(IdentifierScheme expected) {
        return new ApplicationException(
                new ApplicationFailure.InactiveIdentifierScheme(expected.id()));
    }

    private static PartyRegistrationResult asReplay(PartyRegistrationResult result) {
        return new PartyRegistrationResult(
                result.party(),
                result.initialIdentifier(),
                PartyRegistrationOutcome.REPLAYED);
    }
}
