package com.alexastudillo.partyregistry.architecture.fixture.isolation.domain;

import io.quarkus.runtime.StartupEvent;

/**
 * Represents a forbidden Domain dependency on Quarkus.
 */
public final class DomainDependingOnQuarkus {

    public StartupEvent event(StartupEvent event) {
        return event;
    }
}
