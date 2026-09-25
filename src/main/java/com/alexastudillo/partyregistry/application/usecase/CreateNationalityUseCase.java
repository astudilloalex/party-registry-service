package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.CreateNationalityCommand;
import com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome;
import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import com.alexastudillo.partyregistry.application.port.NationalityMutationPort;
import io.smallrye.mutiny.Uni;

import java.util.Objects;

/** Coordinates tenant-scoped keyed creation with lazy recognition of only new country submissions. */
public final class CreateNationalityUseCase {

    private final NationalityMutationPort mutations;
    private final CountryReferencePort countries;

    public CreateNationalityUseCase(NationalityMutationPort mutations, CountryReferencePort countries) {
        this.mutations = Objects.requireNonNull(mutations, "mutations");
        this.countries = Objects.requireNonNull(countries, "countries");
    }

    /** Returns the accepted result or original replay without revalidating completed country input. */
    public Uni<NationalityMutationOutcome> execute(CreateNationalityCommand command) {
        Objects.requireNonNull(command, "command");
        return mutations.create(command, () -> CountryValidation.validateNationalityCountry(
                countries, command.requestMetadata(), command.countryCode()));
    }
}
