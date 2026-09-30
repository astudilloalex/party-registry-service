package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.Party;
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
            Iterable<PartyActivationEvidence> evidence,
            LocalDate evaluatedOn,
            Instant occurredAt,
            String updatedBy) {
        requireDraft(party);
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

    /**
     * Checks lifecycle eligibility before an application workflow requests identifier evidence.
     *
     * @param party the tenant-qualified Party under evaluation
     * @throws DomainValidationException when the Party is absent or not draft
     */
    public void requireDraft(Party party) {
        if (party == null) {
            throw new DomainValidationException(DomainViolation.PARTY_REQUIRED, "Party is required");
        }
        if (party.recordStatus() != PartyRecordStatus.DRAFT) {
            throw new DomainValidationException(
                    DomainViolation.PARTY_ACTIVATION_INVALID_STATE,
                    "Only a draft Party can be activated");
        }
    }

    private static boolean hasQualifyingIdentifier(
            Party party,
            Iterable<PartyActivationEvidence> evidence,
            LocalDate evaluatedOn) {
        for (PartyActivationEvidence candidate : evidence) {
            if (candidate != null && qualifies(party, candidate, evaluatedOn)) {
                return true;
            }
        }
        return false;
    }

    private static boolean qualifies(
            Party party,
            PartyActivationEvidence evidence,
            LocalDate evaluatedOn) {
        LocalDate expiresOn = evidence.expiresOn();
        return evidence.tenantId().equals(party.tenantId())
                && evidence.partyId().equals(party.partyId())
                && evidence.identifierSchemeId().equals(evidence.schemeId())
                && evidence.status() == PartyIdentifierStatus.VERIFIED
                && (expiresOn == null || !expiresOn.isBefore(evaluatedOn))
                && evidence.applicableSubjectType().supports(party.type());
    }
}
