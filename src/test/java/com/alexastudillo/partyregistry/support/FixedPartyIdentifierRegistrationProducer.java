package com.alexastudillo.partyregistry.support;

import com.alexastudillo.partyregistry.application.port.IdentifierProtectionPort;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierFingerprintPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import com.alexastudillo.partyregistry.application.usecase.PartyIdentifierPreparation;
import com.alexastudillo.partyregistry.application.usecase.RegisterPartyIdentifierUseCase;
import com.alexastudillo.partyregistry.domain.policy.IdentifierRuleCatalog;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSchemePolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;

/** Supplies controlled time only to the real additional-registration workflow in its selected HTTP test profile. */
@Alternative
@ApplicationScoped
public class FixedPartyIdentifierRegistrationProducer {
    public static final LocalDate DATE = LocalDate.of(2026, Month.OCTOBER, 9);
    public static final Clock CLOCK = Clock.fixed(DATE.atTime(23, 30, 0, 123456789).toInstant(ZoneOffset.UTC), ZoneOffset.ofHours(14));

    /** Keeps real fingerprinting, protection and transactional persistence while fixing UTC validity evaluation. */
    @Produces
    RegisterPartyIdentifierUseCase registration(PartyIdentifierMutationPort mutations, PartyIdentifierFingerprintPort fingerprints,
            IdentifierProtectionPort protection, OperationObservationPort observation) {
        return new RegisterPartyIdentifierUseCase(mutations, fingerprints,
                new PartyIdentifierPreparation(new IdentifierRuleCatalog(), new IdentifierSchemePolicy(), protection), CLOCK, observation);
    }
}
