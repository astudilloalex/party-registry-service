package com.alexastudillo.partyregistry.support;

import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import com.alexastudillo.partyregistry.application.usecase.PatchPartyIdentifierUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;
import java.time.ZoneOffset;

/** Fixes only PATCH operation time while retaining real guarded writes, events and neutral failure handling. */
@Alternative
@ApplicationScoped
public class FixedPartyIdentifierPatchProducer {
    public static final Clock CLOCK = Clock.fixed(FixedPartyIdentifierRegistrationProducer.CLOCK.instant().plusSeconds(5), ZoneOffset.ofHours(14));

    /** Produces the actual workflow with one deterministic post-creation UTC operation instant. */
    @Produces
    PatchPartyIdentifierUseCase patch(PartyIdentifierMutationPort mutations, OperationObservationPort observation) {
        return new PatchPartyIdentifierUseCase(mutations, CLOCK, observation);
    }
}
