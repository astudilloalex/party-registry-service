package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyActivationCandidate;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.policy.PartyActivationPolicy;
import com.alexastudillo.partyregistry.domain.policy.PartyIdentifierEvidence;

/**
 * Orders transactional activation failures before applying the Domain activation policy.
 */
final class PartyActivationDecision {

    private final PartyActivationPolicy activationPolicy = new PartyActivationPolicy();

    void requireLockedState(
            PartyActivationCandidate candidate,
            PartyVersion currentVersion,
            PartyRecordStatus currentStatus) {
        if (currentVersion == null || currentStatus == null) {
            throw partyNotFound(candidate);
        }
        if (!candidate.expectedVersion().equals(currentVersion)) {
            throw new ApplicationException(new ApplicationFailure.StalePartyVersion(
                    candidate.expectedVersion(),
                    currentVersion));
        }
        if (currentStatus != PartyRecordStatus.DRAFT) {
            throw new ApplicationException(new ApplicationFailure.InvalidPartyLifecycle(
                    candidate.partyId(),
                    currentStatus));
        }
    }

    Party activate(
            PartyActivationCandidate candidate,
            Party party,
            Iterable<PartyIdentifierEvidence> evidence) {
        if (party == null) {
            throw partyNotFound(candidate);
        }
        requireLockedState(candidate, party.version(), party.recordStatus());
        try {
            return activationPolicy.activate(
                    party,
                    evidence,
                    candidate.evaluatedOn(),
                    candidate.occurredAt(),
                    candidate.requestMetadata().userId());
        } catch (DomainValidationException exception) {
            throw switch (exception.violation()) {
                case DomainViolation.PARTY_ACTIVATION_INVALID_STATE -> new ApplicationException(
                        new ApplicationFailure.InvalidPartyLifecycle(
                                candidate.partyId(),
                                party.recordStatus()),
                        exception);
                case DomainViolation.PARTY_ACTIVATION_IDENTIFIER_REQUIRED -> new ApplicationException(
                        new ApplicationFailure.MissingQualifyingIdentifier(candidate.partyId()),
                        exception);
                default -> PersistenceExceptionTranslator.toApplicationException(exception);
            };
        }
    }

    private static ApplicationException partyNotFound(PartyActivationCandidate candidate) {
        return new ApplicationException(new ApplicationFailure.PartyNotFound(
                candidate.partyId(),
                candidate.requestMetadata().tenantId()));
    }
}
