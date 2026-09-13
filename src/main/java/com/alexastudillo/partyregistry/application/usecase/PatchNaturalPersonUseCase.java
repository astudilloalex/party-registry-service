package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.PatchNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import com.alexastudillo.partyregistry.application.port.NaturalPersonRepository;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import io.smallrye.mutiny.Uni;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Orchestrates presence-aware tenant-scoped natural-person updates.
 */
public final class PatchNaturalPersonUseCase {

    private final NaturalPersonRepository repository;
    private final CountryReferencePort countryReferencePort;
    private final Clock clock;

    public PatchNaturalPersonUseCase(
            NaturalPersonRepository repository,
            CountryReferencePort countryReferencePort,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.countryReferencePort = Objects.requireNonNull(countryReferencePort, "countryReferencePort");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Applies present fields only and persists the result using the supplied
     * expected version.
     *
     * @param command presence-aware patch command
     * @return the persisted natural-person result
     */
    public Uni<NaturalPersonResult> execute(PatchNaturalPersonCommand command) {
        Objects.requireNonNull(command, "command");

        return repository.findByTenantAndId(command.tenantId(), command.partyId())
                .onItem().transformToUni(found -> found
                        .map(current -> prepareUpdate(current, command))
                        .orElseGet(() -> Uni.createFrom().failure(notFound(command))))
                .chain(prepared -> CountryValidation.validateChangedCountry(
                        countryReferencePort,
                        command.requestMetadata(),
                        prepared.currentCountryCode(),
                        prepared.updated().details().birthCountryCode())
                        .replaceWith(prepared.updated()))
                .chain(updated -> repository.update(updated, command.expectedVersion()))
                .map(NaturalPersonResult::fromAggregate);
    }

    private Uni<PreparedUpdate> prepareUpdate(
            NaturalPerson current,
            PatchNaturalPersonCommand command) {
        return Uni.createFrom().item(() -> prepareDomainUpdate(current, command))
                .onFailure(DomainValidationException.class)
                .transform(ApplicationException::of);
    }

    private PreparedUpdate prepareDomainUpdate(
            NaturalPerson current,
            PatchNaturalPersonCommand command) {
        verifyVersion(current, command);
        Instant occurredAt = clock.instant();
        LocalDate evaluatedOn = LocalDate.ofInstant(occurredAt, clock.getZone());
        NaturalPerson updated = current.patchDetails(
                command.patch(),
                evaluatedOn,
                occurredAt,
                command.requestMetadata().userId());
        return new PreparedUpdate(current.details().birthCountryCode(), updated);
    }

    private static void verifyVersion(
            NaturalPerson current,
            PatchNaturalPersonCommand command) {
        if (!current.version().equals(command.expectedVersion())) {
            throw new ApplicationException(new ApplicationFailure.ExpectedVersionMismatch(
                    command.expectedVersion(),
                    current.version()));
        }
    }

    private static ApplicationException notFound(PatchNaturalPersonCommand command) {
        return new ApplicationException(new ApplicationFailure.NaturalPersonNotFound(
                command.partyId(),
                command.tenantId()));
    }

    /**
     * Retains the prior country alongside the candidate patch result.
     */
    private record PreparedUpdate(@Nullable String currentCountryCode, NaturalPerson updated) {
    }
}
