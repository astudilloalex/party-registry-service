package com.alexastudillo.partyregistry.domain.model;

import org.jspecify.annotations.Nullable;

/** Carries a selected verified target and at most one demoted verified record in the same immutable scope. */
public record PartyIdentifierPrimaryPlan(PartyIdentifier candidate, @Nullable PartyIdentifier formerPrimary) {

    /** Rejects incomplete or cross-owner plans rather than allow partial persistence of an invalid selection. */
    public PartyIdentifierPrimaryPlan {
        if (candidate == null || candidate.status() != PartyIdentifierStatus.VERIFIED || !candidate.isPrimary()) {
            throw new IllegalArgumentException("Primary plan requires selected verified evidence");
        }
        if (formerPrimary != null && (formerPrimary.status() != PartyIdentifierStatus.VERIFIED
                || formerPrimary.isPrimary() || candidate.identifierId().equals(formerPrimary.identifierId())
                || !candidate.sharesPrimaryScope(formerPrimary))) {
            throw new IllegalArgumentException("Primary plan requires a distinct demoted record in the same scope");
        }
    }
}
