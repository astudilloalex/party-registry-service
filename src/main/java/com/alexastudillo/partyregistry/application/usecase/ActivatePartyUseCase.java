package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.ActivatePartyCommand;
import com.alexastudillo.partyregistry.application.model.PartyActivationCandidate;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyActivationPort;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Adds trusted UTC evaluation time to a Party activation request and delegates its atomic outcome.
 */
public final class ActivatePartyUseCase {

    private final PartyActivationPort activationPort;
    private final Clock clock;
    private final OperationObservationPort observationPort;

    /**
     * Creates the activation workflow from its atomic output port and trusted clock.
     *
     * @param activationPort atomic tenant-scoped activation capability
     * @param clock trusted source of activation time
     * @param observationPort bounded operation observation
     */
    public ActivatePartyUseCase(
            PartyActivationPort activationPort,
            Clock clock,
            OperationObservationPort observationPort) {
        this.activationPort = Objects.requireNonNull(activationPort, "activationPort");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.observationPort = Objects.requireNonNull(observationPort, "observationPort");
    }

    /**
     * Delegates one tenant-qualified activation attempt with one trusted UTC evaluation instant.
     *
     * @param command tenant-scoped Party identity and expected version
     * @return the unchanged reactive outcome of the atomic activation port
     */
    public Uni<PartyDetailsResult> execute(ActivatePartyCommand command) {
        Objects.requireNonNull(command, "command");
        return OperationObservation.observe(
                command.requestMetadata(),
                ObservedOperation.APPLICATION_PARTY_ACTIVATION,
                observationPort,
                () -> {
                    Instant occurredAt = clock.instant();
                    LocalDate evaluatedOn = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
                    return activationPort.activate(new PartyActivationCandidate(
                            command.requestMetadata(),
                            command.partyId(),
                            command.expectedVersion(),
                            evaluatedOn,
                            occurredAt));
                },
                ignored -> OperationOutcome.ACTIVATED);
    }
}
