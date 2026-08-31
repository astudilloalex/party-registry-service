package com.alexastudillo.partyregistry.infrastructure.configuration;

import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import com.alexastudillo.partyregistry.application.port.IdempotentNaturalPersonCreationPort;
import com.alexastudillo.partyregistry.application.port.NaturalPersonRepository;
import com.alexastudillo.partyregistry.application.usecase.CreateNaturalPersonUseCase;
import com.alexastudillo.partyregistry.application.usecase.GetNaturalPersonUseCase;
import com.alexastudillo.partyregistry.application.usecase.PatchNaturalPersonUseCase;
import com.alexastudillo.partyregistry.application.usecase.ReplaceNaturalPersonUseCase;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;

/**
 * Composes framework-independent application use cases from infrastructure
 * ports.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class ApplicationUseCaseProducer {

    private static final Clock UTC_CLOCK = Clock.systemUTC();

    @Produces
    CreateNaturalPersonUseCase createNaturalPersonUseCase(
            CountryReferencePort countryReferencePort,
            IdempotentNaturalPersonCreationPort creationPort) {
        return new CreateNaturalPersonUseCase(countryReferencePort, creationPort, UTC_CLOCK);
    }

    @Produces
    GetNaturalPersonUseCase getNaturalPersonUseCase(NaturalPersonRepository repository) {
        return new GetNaturalPersonUseCase(repository);
    }

    @Produces
    ReplaceNaturalPersonUseCase replaceNaturalPersonUseCase(
            NaturalPersonRepository repository,
            CountryReferencePort countryReferencePort) {
        return new ReplaceNaturalPersonUseCase(repository, countryReferencePort, UTC_CLOCK);
    }

    @Produces
    PatchNaturalPersonUseCase patchNaturalPersonUseCase(
            NaturalPersonRepository repository,
            CountryReferencePort countryReferencePort) {
        return new PatchNaturalPersonUseCase(repository, countryReferencePort, UTC_CLOCK);
    }
}
