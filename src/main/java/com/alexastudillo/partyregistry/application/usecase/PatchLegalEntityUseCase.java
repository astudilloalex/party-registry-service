package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.PatchLegalEntityCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.LegalEntityResult;
import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import com.alexastudillo.partyregistry.application.port.LegalEntityRepository;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import io.smallrye.mutiny.Uni;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

/** Coordinates presence-aware legal-detail corrections with version and changed-country validation. */
public final class PatchLegalEntityUseCase {

    private final LegalEntityRepository repository;
    private final CountryReferencePort countryReferencePort;
    private final Clock clock;

    public PatchLegalEntityUseCase(
            LegalEntityRepository repository, CountryReferencePort countryReferencePort, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.countryReferencePort = Objects.requireNonNull(countryReferencePort, "countryReferencePort");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Applies present fields after checking the version and persists only a fully validated candidate.
     *
     * @param command partial correction inputs and trusted context
     * @return the accepted representation at its persisted version, or a transport-neutral failure
     */
    public Uni<LegalEntityResult> execute(PatchLegalEntityCommand command) {
        Objects.requireNonNull(command, "command");
        return Uni.createFrom().deferred(() -> repository.findByTenantAndId(command.tenantId(), command.partyId()))
                .map(found -> found.orElseThrow(() -> new ApplicationException(
                        new ApplicationFailure.LegalEntityNotFound(command.partyId(), command.tenantId()))))
                .flatMap(current -> Uni.createFrom().item(() -> prepare(current, command))
                        .onFailure(DomainValidationException.class).transform(ApplicationException::of))
                .call(prepared -> CountryValidation.validateChangedIncorporationCountry(countryReferencePort,
                        command.requestMetadata(), prepared.currentCountryCode(),
                        prepared.updated().details().incorporationCountryCode()))
                .flatMap(prepared -> repository.update(prepared.updated(), command.expectedVersion()))
                .map(LegalEntityResult::fromAggregate);
    }

    private PreparedUpdate prepare(LegalEntity current, PatchLegalEntityCommand command) {
        if (!current.version().equals(command.expectedVersion())) {
            throw new ApplicationException(new ApplicationFailure.ExpectedVersionMismatch(
                    command.expectedVersion(), current.version()));
        }
        Instant occurredAt = clock.instant();
        LocalDate evaluatedOn = LocalDate.ofInstant(occurredAt, ZoneOffset.UTC);
        LegalEntity updated = current.patchDetails(command.patch(), evaluatedOn,
                occurredAt, command.requestMetadata().userId());
        return new PreparedUpdate(current.details().incorporationCountryCode(), updated);
    }

    /** Retains the previous country without keeping a persistence session across external validation. */
    private record PreparedUpdate(String currentCountryCode, LegalEntity updated) {
    }
}
