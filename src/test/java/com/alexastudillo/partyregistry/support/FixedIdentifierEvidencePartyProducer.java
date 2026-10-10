package com.alexastudillo.partyregistry.support;

import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyMutationPort;
import com.alexastudillo.partyregistry.application.usecase.ChangePartyLifecycleUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

/** Aligns Party activation time after identifier verification in identity-evidence compatibility tests. */
@Alternative
@ApplicationScoped
public class FixedIdentifierEvidencePartyProducer {
    /** Retains the actual Party workflow/policies with a deterministic post-verification instant. */
    @Produces
    ChangePartyLifecycleUseCase lifecycle(PartyMutationPort mutations, OperationObservationPort observation) {
        return new ChangePartyLifecycleUseCase(mutations, FixedPartyIdentifierPrimaryProducer.CLOCK, observation);
    }
}
