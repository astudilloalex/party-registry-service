package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.PatchPartyCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyMutationPort;
import com.alexastudillo.partyregistry.application.support.UuidV7;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.model.Party;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/** Orders absence, version, and Domain label validation before atomically storing a root correction and its enabled event. */
public final class PatchPartyUseCase {

    private final PartyMutationPort mutations;
    private final Clock clock;
    private final OperationObservationPort observations;

    public PatchPartyUseCase(PartyMutationPort mutations, Clock clock, OperationObservationPort observations) {
        this.mutations = Objects.requireNonNull(mutations, "mutations");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.observations = Objects.requireNonNull(observations, "observations");
    }

    /** Corrects labels in every lifecycle state, without using lifecycle keys or replay behavior. */
    public Uni<PartyMutationOutcome> execute(PatchPartyCommand command) {
        Objects.requireNonNull(command, "command");
        return OperationObservation.observe(command.requestMetadata(), ObservedOperation.APPLICATION_PARTY_PATCH, observations,
                () -> mutations.execute(command.requestMetadata(), context ->
                context.findForUpdate(command.partyId()).map(found -> prepare(found, command))
                        .onFailure(DomainValidationException.class).transform(PatchPartyUseCase::translateDomain)
                        .chain(candidate -> context.persistRoot(candidate, command.expectedVersion()))
                        .call(stored -> appendEvent(context, command, stored))
                        .map(stored -> new PartyMutationOutcome(PartyDetailsResult.fromAggregate(stored), PartyMutationOutcome.Disposition.APPLIED))),
                ignored -> OperationOutcome.APPLIED);
    }

    private Party prepare(Optional<Party> found, PatchPartyCommand command) {
        Party current = found.orElseThrow(() -> new ApplicationException(
                new ApplicationFailure.PartyNotFound(command.partyId(), command.requestMetadata().tenantId())));
        if (!command.expectedVersion().equals(current.version())) {
            throw new ApplicationException(new ApplicationFailure.ExpectedVersionMismatch(command.expectedVersion(), current.version()));
        }
        Instant occurredAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return current.correctDisplayName(command.displayName(), occurredAt, command.requestMetadata().userId());
    }

    private static Uni<Void> appendEvent(PartyMutationContext context, PatchPartyCommand command, Party stored) {
        return context.appendEnabledEvent(new PartyChangedOutboxCandidate(UuidV7.generate(stored.auditInfo().updatedAt()),
                stored.tenantId(), stored.partyId(), stored.version(), stored.type(), stored.recordStatus(),
                PartyChangedOutboxCandidate.Kind.UPDATED, stored.auditInfo().updatedAt(), command.requestMetadata().processId(),
                command.requestMetadata().userId()));
    }

    private static RuntimeException translateDomain(DomainValidationException failure) {
        return switch (failure.violation()) {
            case DISPLAY_NAME_REQUIRED, DISPLAY_NAME_TOO_LONG -> ApplicationException.of(failure);
            default -> new IllegalStateException("Root correction invariant failed", failure);
        };
    }
}
