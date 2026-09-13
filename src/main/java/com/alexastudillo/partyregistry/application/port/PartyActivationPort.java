package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.PartyActivationCandidate;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import io.smallrye.mutiny.Uni;

/**
 * Applies tenant-scoped Party activation and enabled events as one atomic outcome.
 */
public interface PartyActivationPort {

    /**
     * Activates a Party using the expected version and trusted evaluation time.
     *
     * @param candidate activation request and evaluation times
     * @return the activated Party details
     */
    Uni<PartyDetailsResult> activate(PartyActivationCandidate candidate);
}
