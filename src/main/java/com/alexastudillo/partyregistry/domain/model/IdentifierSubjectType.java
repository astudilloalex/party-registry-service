package com.alexastudillo.partyregistry.domain.model;

/**
 * Declares which immutable Party types may use an identifier scheme.
 */
public enum IdentifierSubjectType {
    NATURAL_PERSON,
    LEGAL_ENTITY,
    BOTH;

    public boolean supports(PartyType partyType) {
        return this == BOTH
                || this == NATURAL_PERSON && partyType == PartyType.NATURAL_PERSON
                || this == LEGAL_ENTITY && partyType == PartyType.LEGAL_ENTITY;
    }
}
