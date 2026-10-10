package com.alexastudillo.partyregistry.support;

import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierFingerprintPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import com.alexastudillo.partyregistry.application.usecase.SetPrimaryPartyIdentifierUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;
import java.time.ZoneOffset;

/** Fixes primary-selection time while retaining actual guarded replacement, replay and outbox persistence. */
@Alternative
@ApplicationScoped
public class FixedPartyIdentifierPrimaryProducer {
    public static final Clock CLOCK = Clock.fixed(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().plusSeconds(20), ZoneOffset.ofHours(14));

    /** Composes the real workflow with a deterministic post-verification operation instant. */
    @Produces
    SetPrimaryPartyIdentifierUseCase primary(PartyIdentifierMutationPort mutations,
            PartyIdentifierFingerprintPort fingerprints, OperationObservationPort observation) {
        return new SetPrimaryPartyIdentifierUseCase(mutations, fingerprints, CLOCK, observation);
    }
}
