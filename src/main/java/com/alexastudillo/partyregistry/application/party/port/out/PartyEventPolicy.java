package com.alexastudillo.partyregistry.application.party.port.out;

import io.smallrye.mutiny.Uni;

public interface PartyEventPolicy {

    Uni<Boolean> shouldRecord(String eventType);
}
