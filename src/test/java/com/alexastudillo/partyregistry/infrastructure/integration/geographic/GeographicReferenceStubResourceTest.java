package com.alexastudillo.partyregistry.infrastructure.integration.geographic;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Prevents a shared HTTP fixture from replacing normal service timeout configuration with fault-test budgets. */
class GeographicReferenceStubResourceTest {

    @Test
    void overridesOnlyTheLocalProviderUrl() {
        var resource = new GeographicReferenceStubResource();
        try {
            var overrides = resource.start();
            assertEquals(Set.of("quarkus.rest-client.geographic-reference.url"), overrides.keySet());
        } finally {
            resource.stop();
        }
    }
}
