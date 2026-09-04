package com.alexastudillo.partyregistry.architecture.fixture.isolation.application;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represents a forbidden Application dependency on JSON infrastructure.
 */
public final class ApplicationDependingOnJackson {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ObjectMapper objectMapper() {
        return objectMapper;
    }
}
