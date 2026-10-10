package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierPrimaryPlan;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** Coordinates primary selection and provisional verification using synchronous immutable identifiers. */
public final class PartyIdentifierPrimaryPolicy {

    /**
     * Validates both prospective records before returning a complete immutable selection plan.
     *
     * @param candidate target evidence; state, capacity and UTC validity are checked before ownership
     * @param currentPrimary observed record; only a different scoped verified primary counts
     * @param replaceCurrentPrimary explicit consent to demote a different scoped primary
     * @param occurredAt one modification instant for both affected identifiers
     * @param updatedBy accepted modifying operator
     * @return target plus the demoted former primary only when replacement is necessary
     * @throws DomainValidationException when eligibility, capacity, ownership or audit prevents the whole plan
     */
    public PartyIdentifierPrimaryPlan planSelection(PartyIdentifier candidate, @Nullable PartyIdentifier currentPrimary,
            boolean replaceCurrentPrimary, Instant occurredAt, String updatedBy) {
        requireCandidate(candidate);
        PartyIdentifier selected = candidate.selectPrimary(occurredAt, updatedBy);
        if (currentPrimary != null && isDifferentVerifiedPrimary(candidate, currentPrimary)) {
            if (!replaceCurrentPrimary) throw primaryConflict();
            return new PartyIdentifierPrimaryPlan(selected, currentPrimary.demotePrimary(occurredAt, updatedBy));
        }
        return new PartyIdentifierPrimaryPlan(selected, null);
    }

    /**
     * Confirms pending evidence without implicitly replacing another scoped verified primary.
     *
     * @param candidate pending target; lifecycle, capacity and validity precede primary conflict
     * @param currentPrimary observed primary; provisional, unrelated and nonprimary records do not conflict
     * @param occurredAt one verification/modification instant
     * @param updatedBy accepted verification operator
     * @return verified immutable evidence retaining its original primary flag
     * @throws DomainValidationException when target eligibility or scoped ownership prevents confirmation
     */
    public PartyIdentifier verify(PartyIdentifier candidate, @Nullable PartyIdentifier currentPrimary,
            Instant occurredAt, String updatedBy) {
        requireCandidate(candidate);
        PartyIdentifier verified = candidate.verify(occurredAt, updatedBy);
        if (candidate.isPrimary() && currentPrimary != null && isDifferentVerifiedPrimary(candidate, currentPrimary)) {
            throw primaryConflict();
        }
        return verified;
    }

    private static boolean isDifferentVerifiedPrimary(PartyIdentifier candidate, PartyIdentifier current) {
        return !candidate.identifierId().equals(current.identifierId())
                && candidate.sharesPrimaryScope(current) && current.status() == PartyIdentifierStatus.VERIFIED
                && current.isPrimary();
    }

    private static void requireCandidate(PartyIdentifier candidate) {
        if (candidate == null) {
            throw new DomainValidationException(DomainViolation.PARTY_IDENTIFIER_REQUIRED, "Identifier candidate is required");
        }
    }

    private static DomainValidationException primaryConflict() {
        return new DomainValidationException(DomainViolation.PRIMARY_IDENTIFIER_CONFLICT,
                "Another verified primary owns the identifier scope");
    }
}
