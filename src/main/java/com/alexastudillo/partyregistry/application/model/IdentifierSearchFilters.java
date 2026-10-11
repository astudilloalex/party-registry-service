package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Canonicalizes both historical state dimensions without treating empty sets as omitted filters. */
public record IdentifierSearchFilters(Set<PartyIdentifierStatus> identifierStatuses, Set<PartyRecordStatus> partyRecordStatuses) {
    public IdentifierSearchFilters {
        identifierStatuses = Set.copyOf(identifierStatuses);
        partyRecordStatuses = Set.copyOf(partyRecordStatuses);
    }

    /** Expands omitted dimensions to every stored state and removes duplicate/order differences. */
    public static IdentifierSearchFilters of(@Nullable List<PartyIdentifierStatus> identifiers,
            @Nullable List<PartyRecordStatus> parties) {
        return new IdentifierSearchFilters(identifiers == null ? EnumSet.allOf(PartyIdentifierStatus.class) : Set.copyOf(identifiers),
                parties == null ? EnumSet.allOf(PartyRecordStatus.class) : Set.copyOf(parties));
    }

    public boolean matchesNothing() {
        return identifierStatuses.isEmpty() || partyRecordStatuses.isEmpty();
    }
}
