package com.alexastudillo.partyregistry.architecture.fixture.direction.api;

import com.alexastudillo.partyregistry.architecture.fixture.direction.infrastructure.InfrastructureFixture;

/**
 * Represents a forbidden API dependency on Infrastructure.
 */
public final class ApiDependingOnInfrastructure {

    private final InfrastructureFixture infrastructure = new InfrastructureFixture();

    public InfrastructureFixture infrastructure() {
        return infrastructure;
    }
}
