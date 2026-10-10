package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.PatchPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.error.PartyIdentifierFailures;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import com.alexastudillo.partyregistry.application.support.UuidV7;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Coordinates independent current-version metadata correction in one owned identifier transaction. */
public final class PatchPartyIdentifierUseCase {
    private final PartyIdentifierMutationPort mutations;
    private final Clock clock;
    private final OperationObservationPort observation;

    /** Composes transaction ownership, trusted operation time and bounded observation. */
    public PatchPartyIdentifierUseCase(PartyIdentifierMutationPort mutations, Clock clock, OperationObservationPort observation) {
        this.mutations = Objects.requireNonNull(mutations, "mutations");
        this.clock = Objects.requireNonNull(clock, "clock"); this.observation = Objects.requireNonNull(observation, "observation");
    }

    /** Returns only a complete accepted metadata outcome without acquiring replay semantics. */
    public Uni<PartyIdentifierMutationOutcome> execute(PatchPartyIdentifierCommand command) {
        Objects.requireNonNull(command, "command");
        return OperationObservation.observe(command.requestMetadata(), ObservedOperation.APPLICATION_PARTY_IDENTIFIER_PATCH, observation,
                () -> mutations.execute(command.requestMetadata(), context -> correct(context, command)), _ -> OperationOutcome.APPLIED);
    }

    private Uni<PartyIdentifierMutationOutcome> correct(PartyIdentifierMutationContext context, PatchPartyIdentifierCommand command) {
        return PartyIdentifierMutationScope.locked(context, command.requestMetadata(), command.partyId(), command.identifierId(), command.expectedVersion())
                .chain(current -> prepare(current, command))
                .chain(candidate -> context.updateIdentifier(candidate, command.expectedVersion()))
                .call(result -> context.appendEnabledEvent(event(command, result)))
                .map(result -> new PartyIdentifierMutationOutcome(result, PartyIdentifierMutationOutcome.Disposition.APPLIED));
    }

    private Uni<PartyIdentifier> prepare(PartyIdentifier current, PatchPartyIdentifierCommand command) {
        return Uni.createFrom().item(() -> current.correctMetadata(command.issuerCode(), command.issuedOn(), command.expiresOn(),
                        clock.instant().truncatedTo(ChronoUnit.MICROS), command.requestMetadata().userId()))
                .onFailure(DomainValidationException.class).transform(PartyIdentifierFailures::translate);
    }

    private static PartyIdentifierChangedOutboxCandidate event(PatchPartyIdentifierCommand command, PartyIdentifierResult result) {
        return new PartyIdentifierChangedOutboxCandidate(UuidV7.generate(result.updatedAt()), command.requestMetadata().tenantId(),
                result.identifierId(), result.version(), result.partyId(), result.schemeCode(), result.status(), result.isPrimary(),
                PartyIdentifierChangedOutboxCandidate.Kind.UPDATED, result.updatedAt(), command.requestMetadata().processId(), command.requestMetadata().userId());
    }
}
