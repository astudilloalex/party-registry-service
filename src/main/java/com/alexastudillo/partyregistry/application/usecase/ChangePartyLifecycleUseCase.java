package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.ChangePartyLifecycleCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.CompletedPartyLifecycle;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyActivatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyMutationPort;
import com.alexastudillo.partyregistry.application.support.UuidV7;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.policy.PartyActivationPolicy;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves serialized historical replay before orchestrating root version
 * checks, Domain lifecycle decisions, and atomic event intent.
 */
public final class ChangePartyLifecycleUseCase {

    private final PartyMutationPort mutations;
    private final Clock clock;
    private final OperationObservationPort observation;
    private final PartyActivationPolicy activation = new PartyActivationPolicy();

    /** Composes lifecycle orchestration with trusted time and subscription-scoped observation. */
    public ChangePartyLifecycleUseCase(PartyMutationPort mutations, Clock clock,
            OperationObservationPort observation) {
        this.mutations = Objects.requireNonNull(mutations, "mutations");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.observation = Objects.requireNonNull(observation, "observation");
    }

    /**
     * Returns an original accepted result on equivalent replay; otherwise applies
     * exactly one eligible current-version transition.
     */
    public Uni<PartyMutationOutcome> execute(ChangePartyLifecycleCommand command) {
        Objects.requireNonNull(command, "command");
        ObservedOperation operation = switch (command.action()) {
            case ACTIVATE -> ObservedOperation.APPLICATION_PARTY_ACTIVATION;
            case DEACTIVATE -> ObservedOperation.APPLICATION_PARTY_DEACTIVATION;
            case ARCHIVE -> ObservedOperation.APPLICATION_PARTY_ARCHIVAL;
        };
        return OperationObservation.observe(command.requestMetadata(), operation, observation,
                () -> mutations.execute(command.requestMetadata(), context -> resolve(context, command)),
                outcome -> successOutcome(command, outcome));
    }

    private static OperationOutcome successOutcome(ChangePartyLifecycleCommand command, PartyMutationOutcome outcome) {
        if (outcome.disposition() == PartyMutationOutcome.Disposition.REPLAYED) {
            return OperationOutcome.REPLAYED;
        }
        return switch (command.action()) {
            case ACTIVATE -> OperationOutcome.ACTIVATED;
            case DEACTIVATE -> OperationOutcome.DEACTIVATED;
            case ARCHIVE -> OperationOutcome.ARCHIVED;
        };
    }

    private Uni<PartyMutationOutcome> resolve(PartyMutationContext context, ChangePartyLifecycleCommand command) {
        if (command.idempotencyKey().isEmpty()) {
            return change(context, command);
        }
        String key = command.idempotencyKey().orElseThrow();
        return context.serializeReplayKey(command.action(), key)
                .chain(() -> context.findCompleted(command.action(), key))
                .chain(found -> found.isPresent()
                        ? Uni.createFrom().item(() -> replay(command, key, found.orElseThrow()))
                        : change(context, command));
    }

    private static PartyMutationOutcome replay(ChangePartyLifecycleCommand command, String key,
            CompletedPartyLifecycle completed) {
        if (!command.effectiveRequest().equals(completed.request())) {
            throw new ApplicationException(new ApplicationFailure.IdempotencyKeyConflict(key));
        }
        return new PartyMutationOutcome(completed.result(), PartyMutationOutcome.Disposition.REPLAYED);
    }

    private Uni<PartyMutationOutcome> change(PartyMutationContext context, ChangePartyLifecycleCommand command) {
        return context.findForUpdate(command.partyId()).map(found -> requireCurrent(found, command))
                .chain(current -> prepare(context, command, current))
                .chain(candidate -> context.persistRoot(candidate, command.expectedVersion())
                        .onFailure(ApplicationException.class)
                        .transform(ChangePartyLifecycleUseCase::translateWriteConflict))
                .chain(stored -> complete(context, command, stored));
    }

    private static Party requireCurrent(Optional<Party> found, ChangePartyLifecycleCommand command) {
        Party current = found.orElseThrow(() -> new ApplicationException(
                new ApplicationFailure.PartyNotFound(command.partyId(), command.requestMetadata().tenantId())));
        if (!command.expectedVersion().equals(current.version())) {
            throw new ApplicationException(
                    new ApplicationFailure.StalePartyVersion(command.expectedVersion(), current.version()));
        }
        return current;
    }

    private Uni<Party> prepare(PartyMutationContext context, ChangePartyLifecycleCommand command, Party current) {
        return Uni.createFrom().deferred(() -> {
            Instant occurredAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            String user = command.requestMetadata().userId();
            return switch (command.action()) {
                case ACTIVATE -> {
                    activation.requireDraft(current);
                    LocalDate evaluatedOn = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
                    yield context.activationEvidence(current.partyId())
                            .map(evidence -> activation.activate(current, evidence, evaluatedOn, occurredAt, user));
                }
                case DEACTIVATE -> Uni.createFrom().item(() -> current.deactivate(occurredAt, user));
                case ARCHIVE -> Uni.createFrom().item(() -> current.archive(occurredAt, user));
            };
        }).onFailure(DomainValidationException.class).transform(failure -> translateDomain(failure, current));
    }

    private static Uni<PartyMutationOutcome> complete(PartyMutationContext context, ChangePartyLifecycleCommand command,
            Party stored) {
        var result = PartyDetailsResult.fromAggregate(stored);
        Uni<Void> completion = command.idempotencyKey().map(key -> context.recordCompletion(command.action(), key,
                new CompletedPartyLifecycle(command.effectiveRequest(), result)))
                .orElseGet(() -> Uni.createFrom().voidItem());
        return completion.chain(() -> context.appendEnabledEvent(event(command, stored)))
                .replaceWith(new PartyMutationOutcome(result, PartyMutationOutcome.Disposition.APPLIED));
    }

    private static OutboxEventCandidate event(ChangePartyLifecycleCommand command, Party stored) {
        UUID eventId = UuidV7.generate(stored.auditInfo().updatedAt());
        return switch (command.action()) {
            case ACTIVATE -> new PartyActivatedOutboxCandidate(eventId, stored.tenantId(), stored.partyId(),
                    stored.version(), stored.type(),
                    stored.recordStatus(), stored.auditInfo().updatedAt(), command.requestMetadata().processId(),
                    command.requestMetadata().userId());
            case DEACTIVATE -> changedEvent(eventId, PartyChangedOutboxCandidate.Kind.DEACTIVATED, command, stored);
            case ARCHIVE -> changedEvent(eventId, PartyChangedOutboxCandidate.Kind.ARCHIVED, command, stored);
        };
    }

    private static PartyChangedOutboxCandidate changedEvent(UUID eventId, PartyChangedOutboxCandidate.Kind kind,
            ChangePartyLifecycleCommand command, Party stored) {
        return new PartyChangedOutboxCandidate(eventId, stored.tenantId(), stored.partyId(), stored.version(),
                stored.type(),
                stored.recordStatus(), kind, stored.auditInfo().updatedAt(), command.requestMetadata().processId(),
                command.requestMetadata().userId());
    }

    private static RuntimeException translateDomain(DomainValidationException failure, Party current) {
        return switch (failure.violation()) {
            case PARTY_ACTIVATION_INVALID_STATE, PARTY_DEACTIVATION_INVALID_STATE, PARTY_ARCHIVAL_INVALID_STATE ->
                new ApplicationException(
                        new ApplicationFailure.InvalidPartyLifecycle(current.partyId(), current.recordStatus()),
                        failure);
            case PARTY_ACTIVATION_IDENTIFIER_REQUIRED -> new ApplicationException(
                    new ApplicationFailure.MissingQualifyingIdentifier(current.partyId()), failure);
            default -> new IllegalStateException("Party lifecycle invariant failed", failure);
        };
    }

    private static ApplicationException translateWriteConflict(ApplicationException failure) {
        if (failure.failure() instanceof ApplicationFailure.ExpectedVersionMismatch(var expected, var current)) {
            return new ApplicationException(new ApplicationFailure.StalePartyVersion(expected, current), failure);
        }
        return failure;
    }
}
