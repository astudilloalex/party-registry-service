package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;

import java.util.List;

/**
 * Defines the aggregate pair and idempotency data submitted for atomic registration.
 */
public sealed interface PartyRegistrationCandidate
        permits NaturalPersonRegistrationCandidate, LegalEntityRegistrationCandidate {

    RequestMetadata requestMetadata();

    String operation();

    String idempotencyKey();

    String registrationFingerprint();

    Party party();

    IdentifierScheme identifierScheme();

    PartyIdentifier initialIdentifier();

    List<OutboxEventCandidate> outboxCandidates();
}
