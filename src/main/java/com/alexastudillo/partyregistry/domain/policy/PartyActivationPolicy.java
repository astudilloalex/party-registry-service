package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Activates a draft Party only when independent identifier evidence qualifies.
 */
public final class PartyActivationPolicy {

    /**
     * Applies activation eligibility and delegates the immutable lifecycle transition to the Party.
     *
     * @param party Party to activate
     * @param evidence identifier and scheme evidence associated with the Party
     * @param evaluatedOn UTC request evaluation date
     * @param occurredAt activation timestamp
     * @param updatedBy modifying user
     * @return the active Party at the next version
     */
    public Party activate(
            Party party,
            Iterable<PartyIdentifierEvidence> evidence,
            LocalDate evaluatedOn,
            Instant occurredAt,
            String updatedBy) {
        if (party == null) {
            throw new DomainValidationException(DomainViolation.PARTY_REQUIRED, "Party is required");
        }
        if (party.recordStatus() != PartyRecordStatus.DRAFT) {
            throw new DomainValidationException(
                    DomainViolation.PARTY_ACTIVATION_INVALID_STATE,
                    "Only a draft Party can be activated");
        }
        if (evaluatedOn == null) {
            throw new DomainValidationException(
                    DomainViolation.EVALUATION_DATE_REQUIRED,
                    "Evaluation date is required");
        }
        if (evidence == null || !hasQualifyingIdentifier(party, evidence, evaluatedOn)) {
            throw new DomainValidationException(
                    DomainViolation.PARTY_ACTIVATION_IDENTIFIER_REQUIRED,
                    "Party activation requires a qualifying verified identifier");
        }
        return party.activate(occurredAt, updatedBy);
    }

    private static boolean hasQualifyingIdentifier(
            Party party,
            Iterable<PartyIdentifierEvidence> evidence,
            LocalDate evaluatedOn) {
        for (PartyIdentifierEvidence candidate : evidence) {
            if (candidate != null && qualifies(party, candidate, evaluatedOn)) {
                return true;
            }
        }
        return false;
    }

    private static boolean qualifies(
            Party party,
            PartyIdentifierEvidence evidence,
            LocalDate evaluatedOn) {
        PartyIdentifier identifier = evidence.identifier();
        return identifier.tenantId().equals(party.tenantId())
                && identifier.partyId().equals(party.partyId())
                && identifier.identifierSchemeId().equals(evidence.scheme().id())
                && identifier.status() == PartyIdentifierStatus.VERIFIED
                && !identifier.isExpiredOn(evaluatedOn)
                && evidence.scheme().supports(party.type());
    }
}
