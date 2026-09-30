package com.alexastudillo.partyregistry.architecture.fixture.isolation.domain;

import io.smallrye.mutiny.Uni;

/**
 * Represents a forbidden Domain dependency on a reactive framework.
 */
public final class DomainDependingOnMutiny {

    public Uni<String> value() {
        return Uni.createFrom().item("forbidden");
    }
}
