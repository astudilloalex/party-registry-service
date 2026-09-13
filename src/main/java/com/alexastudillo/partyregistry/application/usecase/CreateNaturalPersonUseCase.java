package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.RegisterNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NaturalPersonRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import com.alexastudillo.partyregistry.application.port.IdempotentPartyRegistrationPort;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.RegistrationFingerprintPort;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.support.UuidV7;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * Registers natural-person and initial-identifier aggregates as one idempotent
 * outcome.
 */
public final class CreateNaturalPersonUseCase {

    private final CountryReferencePort countryReferencePort;
    private final Clock clock;
    private final RegistrationFingerprintPort fingerprintPort;
    private final IdempotentPartyRegistrationPort registrationPort;
    private final PartyIdentifierPreparation identifierPreparation;
    private final OperationObservationPort observationPort;

    /**
     * Creates the identifier-required registration workflow from application ports
     * and policies.
     *
     * @param fingerprintPort       keyed effective-request fingerprinting
     * @param registrationPort      atomic Party registration
     * @param countryReferencePort  country validation dependency
     * @param identifierPreparation shared identifier eligibility and protection
     *                              coordinator
     * @param clock                 operation clock
     * @param observationPort       bounded operation observation
     */
    public CreateNaturalPersonUseCase(
            RegistrationFingerprintPort fingerprintPort,
            IdempotentPartyRegistrationPort registrationPort,
            CountryReferencePort countryReferencePort,
            PartyIdentifierPreparation identifierPreparation,
            Clock clock,
            OperationObservationPort observationPort) {
        this.countryReferencePort = Objects.requireNonNull(countryReferencePort, "countryReferencePort");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.fingerprintPort = Objects.requireNonNull(fingerprintPort, "fingerprintPort");
        this.registrationPort = Objects.requireNonNull(registrationPort, "registrationPort");
        this.identifierPreparation = Objects.requireNonNull(identifierPreparation, "identifierPreparation");
        this.observationPort = Objects.requireNonNull(observationPort, "observationPort");
    }

    /**
     * Registers or replays a natural person with one required initial identifier.
     *
     * @param command identifier-required natural-person registration
     * @return the created or replayed safe registration result
     */
    public Uni<PartyRegistrationResult> execute(RegisterNaturalPersonCommand command) {
        Objects.requireNonNull(command, "command");

        return OperationObservation.observe(
                command.requestMetadata(),
                ObservedOperation.APPLICATION_NATURAL_PERSON_REGISTRATION,
                observationPort,
                () -> Uni.createFrom().item(() -> fingerprintPort.fingerprint(command))
                        .flatMap(fingerprint -> registrationPort.findCompleted(
                                command.tenantId(),
                                command.operation(),
                                command.idempotencyKey(),
                                fingerprint)
                                .flatMap(completed -> completed.isPresent()
                                        ? Uni.createFrom().item(asReplay(completed.orElseThrow()))
                                        : rejectLegacyKeyAndRegister(command, fingerprint))),
                OperationObservation::registrationOutcome);
    }

    private Uni<PartyRegistrationResult> rejectLegacyKeyAndRegister(
            RegisterNaturalPersonCommand command,
            String fingerprint) {
        return registrationPort.hasCompletedKey(
                command.tenantId(),
                RegisterNaturalPersonCommand.LEGACY_OPERATION,
                command.idempotencyKey())
                .flatMap(reused -> Boolean.TRUE.equals(reused)
                        ? Uni.createFrom().failure(new ApplicationException(
                                new ApplicationFailure.IdempotencyKeyConflict(command.idempotencyKey())))
                        : registerNew(command, fingerprint));
    }

    private Uni<PartyRegistrationResult> registerNew(
            RegisterNaturalPersonCommand command,
            String fingerprint) {
        Instant occurredAt = clock.instant();
        LocalDate evaluatedOn = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);

        return buildNaturalPerson(command, occurredAt, evaluatedOn)
                .call(naturalPerson -> CountryValidation.validateChangedCountry(
                        countryReferencePort,
                        command.requestMetadata(),
                        null,
                        naturalPerson.details().birthCountryCode()))
                .flatMap(naturalPerson -> identifierPreparation
                        .findEligibleScheme(
                                command.initialIdentifier().identifierSchemeCode(),
                                naturalPerson.type())
                        .map(scheme -> registrationCandidate(
                                command,
                                fingerprint,
                                naturalPerson,
                                scheme,
                                occurredAt,
                                evaluatedOn)))
                .flatMap(registrationPort::registerNaturalPerson);
    }

    private Uni<NaturalPerson> buildNaturalPerson(
            RegisterNaturalPersonCommand command,
            Instant occurredAt,
            LocalDate evaluatedOn) {
        return Uni.createFrom().item(() -> NaturalPerson.create(
                new PartyId(UuidV7.generate(occurredAt)),
                command.tenantId(),
                command.displayName(),
                NaturalPersonDetails.forWrite(
                        command.givenNames(),
                        command.familyNames(),
                        command.preferredName(),
                        command.birthDate(),
                        command.dateOfDeath(),
                        command.birthCountryCode()),
                evaluatedOn,
                occurredAt,
                command.requestMetadata().userId()))
                .onFailure(DomainValidationException.class)
                .transform(ApplicationException::of);
    }

    private NaturalPersonRegistrationCandidate registrationCandidate(
            RegisterNaturalPersonCommand command,
            String fingerprint,
            NaturalPerson naturalPerson,
            IdentifierScheme scheme,
            Instant occurredAt,
            LocalDate evaluatedOn) {
        PartyIdentifier identifier = identifierPreparation.createIdentifier(
                command.tenantId(),
                naturalPerson.partyId(),
                scheme,
                command.initialIdentifier(),
                evaluatedOn,
                occurredAt,
                command.requestMetadata().userId());
        return new NaturalPersonRegistrationCandidate(
                command.requestMetadata(),
                command.idempotencyKey(),
                fingerprint,
                naturalPerson,
                scheme,
                identifier,
                registrationEvents(naturalPerson, identifier, scheme, command, occurredAt));
    }

    private static List<OutboxEventCandidate> registrationEvents(
            NaturalPerson naturalPerson,
            PartyIdentifier identifier,
            IdentifierScheme scheme,
            RegisterNaturalPersonCommand command,
            Instant occurredAt) {
        return List.of(
                new PartyCreatedOutboxCandidate(
                        UuidV7.generate(occurredAt),
                        naturalPerson.tenantId(),
                        naturalPerson.partyId(),
                        naturalPerson.version(),
                        naturalPerson.type(),
                        occurredAt,
                        command.requestMetadata().processId(),
                        command.requestMetadata().userId()),
                new PartyIdentifierCreatedOutboxCandidate(
                        UuidV7.generate(occurredAt),
                        identifier.tenantId(),
                        identifier.identifierId(),
                        identifier.version(),
                        identifier.partyId(),
                        scheme.code(),
                        identifier.status(),
                        occurredAt,
                        command.requestMetadata().processId(),
                        command.requestMetadata().userId()));
    }

    private static PartyRegistrationResult asReplay(PartyRegistrationResult result) {
        return new PartyRegistrationResult(
                result.party(),
                result.initialIdentifier(),
                PartyRegistrationOutcome.REPLAYED);
    }
}
