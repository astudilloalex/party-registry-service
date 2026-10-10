package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.RegisterPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierFingerprintPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.util.Objects;

/** Registers or replays independent Party identifiers through one durable Application-owned transaction workflow. */
public final class RegisterPartyIdentifierUseCase {
    private final PartyIdentifierRegistrationWorkflow workflow;
    private final OperationObservationPort observation;

    /** Composes historical retry identity, locked-scheme preparation, trusted time and neutral observation. */
    public RegisterPartyIdentifierUseCase(PartyIdentifierMutationPort mutations, PartyIdentifierFingerprintPort fingerprints,
            PartyIdentifierPreparation preparation, Clock clock, OperationObservationPort observation) {
        workflow = new PartyIdentifierRegistrationWorkflow(mutations, fingerprints, preparation, clock);
        this.observation = Objects.requireNonNull(observation, "observation");
    }

    /** Returns original safe creation data after commit, preserving the existing internal projection contract. */
    public Uni<PartyIdentifierResult> execute(RegisterPartyIdentifierCommand command) {
        return executeOutcome(command).map(PartyIdentifierMutationOutcome::identifier);
    }

    /** Returns original safe data plus committed creation/replay disposition without reevaluating historical eligibility. */
    public Uni<PartyIdentifierMutationOutcome> executeOutcome(RegisterPartyIdentifierCommand command) {
        Objects.requireNonNull(command, "command");
        return OperationObservation.observe(command.requestMetadata(), ObservedOperation.APPLICATION_ADDITIONAL_IDENTIFIER_REGISTRATION,
                observation, () -> workflow.execute(command), result -> result.disposition() == PartyIdentifierMutationOutcome.Disposition.REPLAYED
                        ? OperationOutcome.REPLAYED : OperationOutcome.CREATED);
    }
}
