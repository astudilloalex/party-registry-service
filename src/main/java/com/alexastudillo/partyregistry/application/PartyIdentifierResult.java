package com.alexastudillo.partyregistry.application;

import java.util.UUID;

public record PartyIdentifierResult(UUID id, UUID partyId, UUID schemeId, String maskedValue, long version) {
    public static PartyIdentifierResult from(PartyIdentifierView view) {
        return new PartyIdentifierResult(view.id(), view.partyId(), view.schemeId(), view.maskedValue(), view.version());
    }
}
