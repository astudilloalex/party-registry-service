package com.alexastudillo.partyregistry.support;

import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierFingerprintPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import com.alexastudillo.partyregistry.application.usecase.ChangePartyIdentifierLifecycleUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;
import java.time.ZoneOffset;

/** Fixes lifecycle evaluation time while retaining actual transactions, replay, audit and event adapters. */
@Alternative
@ApplicationScoped
public class FixedPartyIdentifierLifecycleProducer {
    public static final Clock CLOCK = Clock.fixed(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().plusSeconds(10), ZoneOffset.ofHours(14));

    /** Composes the actual workflow with one deterministic post-creation operation instant. */
    @Produces
    ChangePartyIdentifierLifecycleUseCase lifecycle(PartyIdentifierMutationPort mutations,
            PartyIdentifierFingerprintPort fingerprints, OperationObservationPort observation) {
        return new ChangePartyIdentifierLifecycleUseCase(mutations, fingerprints, CLOCK, observation);
    }
}
