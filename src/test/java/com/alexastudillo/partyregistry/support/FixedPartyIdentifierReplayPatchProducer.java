package com.alexastudillo.partyregistry.support;

import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import com.alexastudillo.partyregistry.application.usecase.PatchPartyIdentifierUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

/** Aligns replay-test correction time with lifecycle time so accepted audit never moves backwards. */
@Alternative
@ApplicationScoped
public class FixedPartyIdentifierReplayPatchProducer {
    /** Uses actual metadata orchestration with a consistent clock across the historical replay sequence. */
    @Produces
    PatchPartyIdentifierUseCase patch(PartyIdentifierMutationPort mutations, OperationObservationPort observation) {
        return new PatchPartyIdentifierUseCase(mutations, FixedPartyIdentifierLifecycleProducer.CLOCK, observation);
    }
}
