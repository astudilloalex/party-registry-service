package com.alexastudillo.partyregistry.infrastructure.configuration;

import com.alexastudillo.partyregistry.application.party.port.out.PartyEventPolicy;

import io.smallrye.mutiny.Uni;

/**
 * Configuration-backed Party event policy boundary.
 */
public final class ConfiguredPartyEventPolicy implements PartyEventPolicy {

    @Override
    public Uni<Boolean> shouldRecord(String eventType) {
        return Uni.createFrom().failure(new UnsupportedOperationException("Party event policy is not implemented"));
    }
}
