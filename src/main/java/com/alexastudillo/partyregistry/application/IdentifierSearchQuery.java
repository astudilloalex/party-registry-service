package com.alexastudillo.partyregistry.application;

import com.alexastudillo.partyregistry.domain.PartyIdentifierStatus;
import java.util.UUID;

public record IdentifierSearchQuery(
        UUID partyId,
        UUID schemeId,
        PartyIdentifierStatus status,
        Boolean primary,
        PageRequest page) {
    public IdentifierSearchQuery {
        if (page == null) {
            page = PageRequest.defaults();
        }
    }
}
