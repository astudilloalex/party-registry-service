package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.CreateNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.idempotency.CreateCommandFingerprint;
import com.alexastudillo.partyregistry.application.model.IdempotentCreationResult;
import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import com.alexastudillo.partyregistry.application.port.IdempotentNaturalPersonCreationPort;
import com.alexastudillo.partyregistry.application.support.UuidV7;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Validates and creates natural persons through the atomic idempotency port.
 */
public final class CreateNaturalPersonUseCase {

    private final CountryReferencePort countryReferencePort;
    private final IdempotentNaturalPersonCreationPort creationPort;
    private final Clock clock;

    public CreateNaturalPersonUseCase(
            CountryReferencePort countryReferencePort,
            IdempotentNaturalPersonCreationPort creationPort,
            Clock clock) {
        this.countryReferencePort = Objects.requireNonNull(countryReferencePort, "countryReferencePort");
        this.creationPort = Objects.requireNonNull(creationPort, "creationPort");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates or replays one natural person without blocking or subscribing to
     * the returned pipeline.
     *
     * @param command validated creation command
     * @return the original or replayed creation result
     */
    public Uni<IdempotentCreationResult> execute(CreateNaturalPersonCommand command) {
        Objects.requireNonNull(command, "command");
        String requestHash = CreateCommandFingerprint.hashOf(
                CreateNaturalPersonCommand.OPERATION,
                command);

        return creationPort.findCompleted(
                command.tenantId(),
                command.idempotencyKey(),
                requestHash)
                .chain(completed -> completed
                        .map(Uni.createFrom()::item)
                        .orElseGet(() -> createNew(command, requestHash)));
    }

    private Uni<IdempotentCreationResult> createNew(
            CreateNaturalPersonCommand command,
            String requestHash) {
        return CountryValidation.validateChangedCountry(
                countryReferencePort,
                command.requestMetadata(),
                null,
                command.birthCountryCode())
                .chain(() -> buildNaturalPerson(command))
                .chain(naturalPerson -> creationPort.createIdempotently(
                        command.tenantId(),
                        command.idempotencyKey(),
                        requestHash,
                        naturalPerson));
    }

    private Uni<NaturalPerson> buildNaturalPerson(CreateNaturalPersonCommand command) {
        return Uni.createFrom().item(() -> createAggregate(command))
                .onFailure(DomainValidationException.class)
                .transform(ApplicationException::of);
    }

    private NaturalPerson createAggregate(CreateNaturalPersonCommand command) {
        Instant occurredAt = clock.instant();
        LocalDate evaluatedOn = LocalDate.ofInstant(occurredAt, clock.getZone());
        return NaturalPerson.create(
                new PartyId(UuidV7.generate(occurredAt)),
                command.tenantId(),
                command.displayName(),
                new NaturalPersonDetails(
                        command.givenNames(),
                        command.familyNames(),
                        command.preferredName(),
                        command.birthDate(),
                        command.dateOfDeath(),
                        command.birthCountryCode()),
                evaluatedOn,
                occurredAt,
                command.requestMetadata().userId());
    }
}
