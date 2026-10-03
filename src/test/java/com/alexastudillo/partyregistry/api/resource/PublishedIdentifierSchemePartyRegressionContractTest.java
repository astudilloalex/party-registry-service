package com.alexastudillo.partyregistry.api.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import java.util.Map;

/** Runs the same catalog/evidence regression contracts with published outbox persistence enabled. */
@QuarkusTest
@TestProfile(PublishedIdentifierSchemePartyRegressionContractTest.Profile.class)
class PublishedIdentifierSchemePartyRegressionContractTest extends IdentifierSchemePartyRegressionContractTest {
    @Override protected int firstHistoricalSlot() { return 5; }

    /** Enables real published-mode persistence while leaving delivery scheduling outside this deterministic HTTP/storage contract. */
    public static final class Profile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() {
            return Map.of("party-registry.outbox.mode", "published", "quarkus.rabbitmq.devservices.enabled", "false",
                    "quarkus.scheduler.enabled", "false");
        }
    }
}
