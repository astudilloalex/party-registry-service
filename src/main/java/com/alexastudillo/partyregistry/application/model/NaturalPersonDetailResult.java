package com.alexastudillo.partyregistry.application.model;

import java.util.List;
import java.util.Objects;

/**
 * Combines a natural-person read result with its current masked identifier projections.
 */
public record NaturalPersonDetailResult(NaturalPersonResult person, List<PartyIdentifierResult> identifiers) {

    public NaturalPersonDetailResult {
        Objects.requireNonNull(person, "person");
        identifiers = List.copyOf(identifiers);
        if (identifiers.stream().anyMatch(identifier -> !identifier.partyId().equals(person.partyId()))) {
            throw new IllegalArgumentException("Identifier must belong to the retrieved Party");
        }
    }
}
