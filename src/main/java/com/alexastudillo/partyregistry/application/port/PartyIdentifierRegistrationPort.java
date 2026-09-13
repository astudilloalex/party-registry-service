package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import io.smallrye.mutiny.Uni;

/**
 * Registers a protected additional identifier without mutating its owning Party.
 */
public interface PartyIdentifierRegistrationPort {

    /**
     * Persists the independent identifier and enabled outbox candidates atomically.
     *
     * @param candidate protected identifier registration candidate
     * @return the safe identifier result
     */
    Uni<PartyIdentifierResult> register(PartyIdentifierRegistrationCandidate candidate);
}
