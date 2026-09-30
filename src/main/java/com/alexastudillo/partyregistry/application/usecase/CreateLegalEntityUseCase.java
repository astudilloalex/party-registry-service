package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.RegisterLegalEntityCommand;
import com.alexastudillo.partyregistry.application.command.RegisterNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.LegalEntityRegistrationCandidate;
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
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
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
 * Registers legal-entity and initial-identifier aggregates as one idempotent
 * outcome.
 */
public final class CreateLegalEntityUseCase {

    private final RegistrationFingerprintPort fingerprintPort;
    private final IdempotentPartyRegistrationPort registrationPort;
    private final CountryReferencePort countryReferencePort;
    private final PartyIdentifierPreparation identifierPreparation;
    private final Clock clock;
    private final OperationObservationPort observationPort;

    /**
     * Creates the legal-entity registration workflow from application ports and
     * policies.
     *
     * @param fingerprintPort       keyed effective-request fingerprinting
     * @param registrationPort      atomic Party registration
     * @param countryReferencePort  country validation dependency
     * @param identifierPreparation shared identifier eligibility and protection
     *                              coordinator
     * @param clock                 operation clock
     * @param observationPort       bounded operation observation
     */
    public CreateLegalEntityUseCase(
            RegistrationFingerprintPort fingerprintPort,
            IdempotentPartyRegistrationPort registrationPort,
            CountryReferencePort countryReferencePort,
            PartyIdentifierPreparation identifierPreparation,
            Clock clock,
            OperationObservationPort observationPort) {
        this.fingerprintPort = Objects.requireNonNull(fingerprintPort, "fingerprintPort");
        this.registrationPort = Objects.requireNonNull(registrationPort, "registrationPort");
        this.countryReferencePort = Objects.requireNonNull(countryReferencePort, "countryReferencePort");
        this.identifierPreparation = Objects.requireNonNull(identifierPreparation, "identifierPreparation");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.observationPort = Objects.requireNonNull(observationPort, "observationPort");
    }

    /**
     * Registers or replays a legal entity with one required initial identifier.
     *
     * @param command identifier-required legal-entity registration
     * @return the created or replayed safe registration result
     */
    public Uni<PartyRegistrationResult> execute(RegisterLegalEntityCommand command) {
        Objects.requireNonNull(command, "command");
        return OperationObservation.observe(
                command.requestMetadata(),
                ObservedOperation.APPLICATION_LEGAL_ENTITY_REGISTRATION,
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
            RegisterLegalEntityCommand command,
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
            RegisterLegalEntityCommand command,
            String fingerprint) {
        Instant occurredAt = clock.instant();
        LocalDate evaluatedOn = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);

        return buildLegalEntity(command, occurredAt, evaluatedOn)
                .call(legalEntity -> CountryValidation.validateIncorporationCountry(
                        countryReferencePort,
                        command.requestMetadata(),
                        legalEntity.details().incorporationCountryCode()))
                .flatMap(legalEntity -> identifierPreparation.findEligibleScheme(
                        command.initialIdentifier().identifierSchemeCode(),
                        legalEntity.type())
                        .map(scheme -> registrationCandidate(
                                command,
                                fingerprint,
                                legalEntity,
                                scheme,
                                occurredAt,
                                evaluatedOn)))
                .flatMap(registrationPort::registerLegalEntity);
    }

    private static Uni<LegalEntity> buildLegalEntity(
            RegisterLegalEntityCommand command,
            Instant occurredAt,
            LocalDate evaluatedOn) {
        return Uni.createFrom().item(() -> LegalEntity.create(
                new PartyId(UuidV7.generate(occurredAt)),
                command.tenantId(),
                command.displayName(),
                LegalEntityDetails.forWrite(
                        command.legalName(),
                        command.tradeName(),
                        command.legalFormCode(),
                        command.incorporationCountryCode(),
                        command.incorporatedOn(),
                        command.dissolvedOn()),
                evaluatedOn,
                occurredAt,
                command.requestMetadata().userId()))
                .onFailure(DomainValidationException.class)
                .transform(ApplicationException::of);
    }

    private LegalEntityRegistrationCandidate registrationCandidate(
            RegisterLegalEntityCommand command,
            String fingerprint,
            LegalEntity legalEntity,
            IdentifierScheme scheme,
            Instant occurredAt,
            LocalDate evaluatedOn) {
        PartyIdentifier identifier = identifierPreparation.createIdentifier(
                command.tenantId(),
                legalEntity.partyId(),
                scheme,
                command.initialIdentifier(),
                evaluatedOn,
                occurredAt,
                command.requestMetadata().userId());
        return new LegalEntityRegistrationCandidate(
                command.requestMetadata(),
                command.idempotencyKey(),
                fingerprint,
                legalEntity,
                scheme,
                identifier,
                registrationEvents(legalEntity, identifier, scheme, command, occurredAt));
    }

    private static List<OutboxEventCandidate> registrationEvents(
            LegalEntity legalEntity,
            PartyIdentifier identifier,
            IdentifierScheme scheme,
            RegisterLegalEntityCommand command,
            Instant occurredAt) {
        return List.of(
                new PartyCreatedOutboxCandidate(
                        UuidV7.generate(occurredAt),
                        legalEntity.tenantId(),
                        legalEntity.partyId(),
                        legalEntity.version(),
                        legalEntity.type(),
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
