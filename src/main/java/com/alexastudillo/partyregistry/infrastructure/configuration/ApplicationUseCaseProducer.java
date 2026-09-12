package com.alexastudillo.partyregistry.infrastructure.configuration;

import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import com.alexastudillo.partyregistry.application.port.IdempotentPartyRegistrationPort;
import com.alexastudillo.partyregistry.application.port.IdentifierProtectionPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeRepository;
import com.alexastudillo.partyregistry.application.port.NaturalPersonRepository;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyActivationPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierRegistrationPort;
import com.alexastudillo.partyregistry.application.port.PartyLookupPort;
import com.alexastudillo.partyregistry.application.port.RegistrationFingerprintPort;
import com.alexastudillo.partyregistry.application.usecase.ActivatePartyUseCase;
import com.alexastudillo.partyregistry.application.usecase.CreateLegalEntityUseCase;
import com.alexastudillo.partyregistry.application.usecase.CreateNaturalPersonUseCase;
import com.alexastudillo.partyregistry.application.usecase.GetNaturalPersonUseCase;
import com.alexastudillo.partyregistry.application.usecase.PatchNaturalPersonUseCase;
import com.alexastudillo.partyregistry.application.usecase.RegisterPartyIdentifierUseCase;
import com.alexastudillo.partyregistry.application.usecase.ReplaceNaturalPersonUseCase;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSchemePolicy;
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
            RegistrationFingerprintPort fingerprintPort,
            IdempotentPartyRegistrationPort registrationPort,
            CountryReferencePort countryReferencePort,
            IdentifierSchemeRepository schemeRepository,
            IdentifierProtectionPort protectionPort,
            OperationObservationPort observationPort) {
        return new CreateNaturalPersonUseCase(
                fingerprintPort,
                registrationPort,
                countryReferencePort,
                schemeRepository,
                new IdentifierRuleCatalog(),
                new IdentifierSchemePolicy(),
                protectionPort,
                UTC_CLOCK,
                observationPort);
    }

    @Produces
    CreateLegalEntityUseCase createLegalEntityUseCase(
            RegistrationFingerprintPort fingerprintPort,
            IdempotentPartyRegistrationPort registrationPort,
            CountryReferencePort countryReferencePort,
            IdentifierSchemeRepository schemeRepository,
            IdentifierProtectionPort protectionPort,
            OperationObservationPort observationPort) {
        return new CreateLegalEntityUseCase(
                fingerprintPort,
                registrationPort,
                countryReferencePort,
                schemeRepository,
                new IdentifierRuleCatalog(),
                new IdentifierSchemePolicy(),
                protectionPort,
                UTC_CLOCK,
                observationPort);
    }

    @Produces
    RegisterPartyIdentifierUseCase registerPartyIdentifierUseCase(
            PartyLookupPort partyLookupPort,
            PartyIdentifierRegistrationPort registrationPort,
            IdentifierSchemeRepository schemeRepository,
            IdentifierProtectionPort protectionPort,
            OperationObservationPort observationPort) {
        return new RegisterPartyIdentifierUseCase(
                partyLookupPort,
                registrationPort,
                schemeRepository,
                new IdentifierRuleCatalog(),
                new IdentifierSchemePolicy(),
                protectionPort,
                UTC_CLOCK,
                observationPort);
    }

    @Produces
    ActivatePartyUseCase activatePartyUseCase(
            PartyActivationPort activationPort,
            OperationObservationPort observationPort) {
        return new ActivatePartyUseCase(activationPort, UTC_CLOCK, observationPort);
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
