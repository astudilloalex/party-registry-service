package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.RegisterPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierRegistrationPort;
import com.alexastudillo.partyregistry.application.port.PartyLookupPort;
import com.alexastudillo.partyregistry.application.support.UuidV7;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * Registers an independent additional identifier for an existing tenant-owned Party.
 */
public final class RegisterPartyIdentifierUseCase {

    private final PartyLookupPort partyLookupPort;
    private final PartyIdentifierRegistrationPort registrationPort;
    private final PartyIdentifierPreparation identifierPreparation;
    private final Clock clock;
    private final OperationObservationPort observationPort;

    /**
     * Creates the additional-identifier workflow from application ports and preparation policy.
     *
     * @param partyLookupPort Party existence and type lookup port
     * @param registrationPort identifier persistence and outbox registration port
     * @param identifierPreparation shared identifier eligibility and protection coordinator
     * @param clock operation clock
     * @param observationPort bounded operation observation port
     */
    public RegisterPartyIdentifierUseCase(
            PartyLookupPort partyLookupPort,
            PartyIdentifierRegistrationPort registrationPort,
            PartyIdentifierPreparation identifierPreparation,
            Clock clock,
            OperationObservationPort observationPort) {
        this.partyLookupPort = Objects.requireNonNull(partyLookupPort, "partyLookupPort");
        this.registrationPort = Objects.requireNonNull(registrationPort, "registrationPort");
        this.identifierPreparation = Objects.requireNonNull(identifierPreparation, "identifierPreparation");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.observationPort = Objects.requireNonNull(observationPort, "observationPort");
    }

    /**
     * Registers an identifier without recreating, mutating, or reclassifying its Party.
     *
     * @param command tenant-qualified Party and identifier input
     * @return the safe identifier result
     */
    public Uni<PartyIdentifierResult> execute(RegisterPartyIdentifierCommand command) {
        Objects.requireNonNull(command, "command");
        return OperationObservation.observe(
                command.requestMetadata(),
                ObservedOperation.APPLICATION_ADDITIONAL_IDENTIFIER_REGISTRATION,
                observationPort,
                () -> partyLookupPort.findType(command.tenantId(), command.partyId())
                        .map(found -> found.orElseThrow(() -> new ApplicationException(
                                new ApplicationFailure.PartyNotFound(
                                        command.partyId(),
                                        command.tenantId()))))
                        .flatMap(partyType -> prepareAndRegister(command, partyType)),
                ignored -> OperationOutcome.CREATED);
    }

    private Uni<PartyIdentifierResult> prepareAndRegister(
            RegisterPartyIdentifierCommand command,
            PartyType partyType) {
        Instant occurredAt = clock.instant();
        LocalDate evaluatedOn = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
        return identifierPreparation.findEligibleScheme(
                command.identifier().identifierSchemeCode(),
                partyType)
                .map(scheme -> registrationCandidate(
                        command,
                        partyType,
                        scheme,
                        occurredAt,
                        evaluatedOn))
                .flatMap(registrationPort::register);
    }

    private PartyIdentifierRegistrationCandidate registrationCandidate(
            RegisterPartyIdentifierCommand command,
            PartyType partyType,
            IdentifierScheme scheme,
            Instant occurredAt,
            LocalDate evaluatedOn) {
        PartyIdentifier identifier = identifierPreparation.createIdentifier(
                command.tenantId(),
                command.partyId(),
                scheme,
                command.identifier(),
                evaluatedOn,
                occurredAt,
                command.requestMetadata().userId());
        return new PartyIdentifierRegistrationCandidate(
                command.requestMetadata(),
                command.idempotencyKey(),
                partyType,
                identifier,
                scheme,
                List.of(new PartyIdentifierCreatedOutboxCandidate(
                        UuidV7.generate(occurredAt),
                        identifier.tenantId(),
                        identifier.identifierId(),
                        identifier.version(),
                        identifier.partyId(),
                        scheme.code(),
                        identifier.status(),
                        occurredAt,
                        command.requestMetadata().processId(),
                        command.requestMetadata().userId())));
    }
}
