package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierRegistrationPort;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
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
import java.util.function.Supplier;

/**
 * Atomically persists additional identifiers without changing their owning Party.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactivePartyIdentifierRegistrationAdapter
        implements PartyIdentifierRegistrationPort {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String ACTIVE_IDENTIFIER_UNIQUE_CONSTRAINT =
            "uq_party_identifiers_active_value";

    private final Mutiny.SessionFactory sessionFactory;
    private final IdentifierSchemePersistenceMapper identifierSchemeMapper;
    private final PartyIdentifierPersistenceMapper identifierMapper;
    private final PartyOutboxEventPersistenceMapper outboxMapper;
    private final IdentifierSchemePolicy identifierSchemePolicy;
    private final PartyIdentifierRegistrationWriteSequence writeSequence;
    private final PartyOutboxPersistenceMode outboxMode;
    private final OperationObservationPort observationPort;

    /**
     * Creates the additional-identifier adapter from its reactive persistence collaborators.
     */
    @Inject
    public HibernateReactivePartyIdentifierRegistrationAdapter(
            Mutiny.SessionFactory sessionFactory,
            IdentifierSchemePersistenceMapper identifierSchemeMapper,
            PartyIdentifierPersistenceMapper identifierMapper,
            PartyOutboxEventPersistenceMapper outboxMapper,
            OperationObservationPort observationPort,
            @ConfigProperty(name = "party-registry.outbox.mode") String outboxMode) {
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.identifierSchemeMapper = Objects.requireNonNull(
                identifierSchemeMapper,
                "identifierSchemeMapper");
        this.identifierMapper = Objects.requireNonNull(identifierMapper, "identifierMapper");
        this.outboxMapper = Objects.requireNonNull(outboxMapper, "outboxMapper");
        this.identifierSchemePolicy = new IdentifierSchemePolicy();
        this.writeSequence = new PartyIdentifierRegistrationWriteSequence();
        this.outboxMode = PartyOutboxPersistenceMode.fromConfiguration(outboxMode);
        this.observationPort = Objects.requireNonNull(observationPort, "observationPort");
    }

    @Override
    public Uni<PartyIdentifierResult> register(PartyIdentifierRegistrationCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return OperationObservation.observe(
                candidate.requestMetadata(),
                ObservedOperation.TRANSACTION_ADDITIONAL_IDENTIFIER_REGISTRATION,
                observationPort,
                () -> registerTransaction(candidate),
                ignored -> OperationOutcome.CREATED);
    }

    private Uni<PartyIdentifierResult> registerTransaction(
            PartyIdentifierRegistrationCandidate candidate) {
        Uni<PartyIdentifierResult> operation = Uni.createFrom().deferred(() -> {
            PartyIdentifierEntity identifier = identifierMapper.toEntity(candidate.identifier());
            List<PartyOutboxEventEntity> outboxEvents = enabledOutboxEvents(candidate);
            PartyIdentifierResult result = PartyIdentifierResult.fromAggregate(
                    candidate.identifier(),
                    candidate.identifierScheme());

            return sessionFactory.withTransaction((session, transaction) -> recheckParty(session, candidate)
                    .call(() -> recheckIdentifierScheme(session, candidate))
                    .call(() -> persistIdentifierAndOutbox(
                            session,
                            identifier,
                            outboxEvents))
                    .replaceWith(result));
        });
        return operation.onFailure().transform(failure -> translateFailure(
                failure,
                candidate.identifierScheme().id()));
    }

    private Uni<Void> recheckParty(
            Mutiny.Session session,
            PartyIdentifierRegistrationCandidate candidate) {
        return session.createQuery("""
                from PartyEntity party
                where party.tenantId = :tenantId
                  and party.id = :partyId
                """, PartyEntity.class)
                .setParameter("tenantId", candidate.identifier().tenantId().value())
                .setParameter("partyId", candidate.identifier().partyId().value())
                .setLockMode(LockMode.PESSIMISTIC_READ)
                .getSingleResultOrNull()
                .invoke(entity -> {
                    if (entity == null || entity.type() != candidate.partyType()) {
                        throw partyNotFound(candidate);
                    }
                })
                .replaceWithVoid();
    }

    private Uni<Void> recheckIdentifierScheme(
            Mutiny.Session session,
            PartyIdentifierRegistrationCandidate candidate) {
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
            PartyIdentifierRegistrationCandidate candidate) {
        if (entity == null) {
            throw unavailableScheme(expected);
        }
        IdentifierScheme current = identifierSchemeMapper.toDomain(entity);
        if (!hasExactStableIdentity(current, expected)
                || !current.version().equals(expected.version())) {
            throw unavailableScheme(expected);
        }
        try {
            identifierSchemePolicy.requireRegistrationEligibility(current, candidate.partyType());
        } catch (DomainValidationException exception) {
            throw switch (exception.violation()) {
                case DomainViolation.IDENTIFIER_SCHEME_INACTIVE -> new ApplicationException(
                        new ApplicationFailure.InactiveIdentifierScheme(expected.id()),
                        exception);
                case DomainViolation.IDENTIFIER_SCHEME_INCOMPATIBLE -> new ApplicationException(
                        new ApplicationFailure.IncompatibleIdentifierScheme(
                                expected.id(),
                                candidate.partyType()),
                        exception);
                default -> PersistenceExceptionTranslator.toApplicationException(exception);
            };
        }
    }

    private Uni<Void> persistIdentifierAndOutbox(
            Mutiny.Session session,
            PartyIdentifierEntity identifier,
            List<PartyOutboxEventEntity> outboxEvents) {
        List<Supplier<Uni<?>>> outboxWrites = outboxEvents.stream()
                .<Supplier<Uni<?>>>map(event -> () -> session.persist(event))
                .toList();
        return writeSequence.execute(
                () -> session.persist(identifier),
                outboxWrites,
                session::flush);
    }

    private List<PartyOutboxEventEntity> enabledOutboxEvents(
            PartyIdentifierRegistrationCandidate candidate) {
        if (!outboxMode.storesEvents()) {
            return List.of();
        }
        return candidate.outboxCandidates().stream()
                .map(event -> requireMatchingIdentifierEvent(event, candidate))
                .map(outboxMapper::toEntity)
                .toList();
    }

    private static PartyIdentifierCreatedOutboxCandidate requireMatchingIdentifierEvent(
            OutboxEventCandidate candidateEvent,
            PartyIdentifierRegistrationCandidate candidate) {
        if (!(candidateEvent instanceof PartyIdentifierCreatedOutboxCandidate event)
                || !event.tenantId().equals(candidate.identifier().tenantId())
                || !event.identifierId().equals(candidate.identifier().identifierId())
                || !event.identifierVersion().equals(candidate.identifier().version())
                || !event.partyId().equals(candidate.identifier().partyId())
                || !event.schemeCode().equals(candidate.identifierScheme().code())
                || event.status() != candidate.identifier().status()) {
            throw new IllegalArgumentException(
                    "Additional identifier outbox event does not match its aggregate");
        }
        return event;
    }

    static Throwable translateFailure(Throwable failure, IdentifierSchemeId schemeId) {
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(schemeId, "schemeId");
        if (!PersistenceExceptionTranslator.requiresTranslation(failure)) {
            return failure;
        }
        if (PersistenceExceptionTranslator.isConstraint(
                failure,
                UNIQUE_VIOLATION,
                ACTIVE_IDENTIFIER_UNIQUE_CONSTRAINT)) {
            return new ApplicationException(
                    new ApplicationFailure.IdentifierUniquenessConflict(schemeId),
                    failure);
        }
        return PersistenceExceptionTranslator.toApplicationException(failure);
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

    private static ApplicationException partyNotFound(
            PartyIdentifierRegistrationCandidate candidate) {
        return new ApplicationException(new ApplicationFailure.PartyNotFound(
                candidate.identifier().partyId(),
                candidate.identifier().tenantId()));
    }

    private static ApplicationException unavailableScheme(IdentifierScheme expected) {
        return new ApplicationException(
                new ApplicationFailure.InactiveIdentifierScheme(expected.id()));
    }
}
