package com.alexastudillo.partyregistry.architecture.fixture.isolation.domain;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represents a forbidden Domain dependency on Jackson.
 */
public final class DomainDependingOnJackson {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ObjectMapper objectMapper() {
        return objectMapper;
    }
}
