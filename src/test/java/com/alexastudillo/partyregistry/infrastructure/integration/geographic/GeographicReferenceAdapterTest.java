package com.alexastudillo.partyregistry.infrastructure.integration.geographic;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.infrastructure.integration.geographic.adapter.GeographicReferenceAdapter;
import com.alexastudillo.partyregistry.infrastructure.integration.geographic.client.GeographicReferenceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.alexastudillo.partyregistry.infrastructure.integration.geographic.GeographicReferenceStubResource.PROCESS_ID;
import static com.alexastudillo.partyregistry.infrastructure.integration.geographic.GeographicReferenceStubResource.TENANT_ID;
import static com.alexastudillo.partyregistry.infrastructure.integration.geographic.GeographicReferenceStubResource.USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies Geographic Reference HTTP outcomes at the controlled network
 * boundary.
 */
@QuarkusTest
@TestProfile(GeographicReferenceAdapterTest.GeographicReferenceProfile.class)
@QuarkusTestResource(GeographicReferenceStubResource.class)
class GeographicReferenceAdapterTest {

    private static final Duration MAXIMUM_WAIT = Duration.ofSeconds(2);

    @Inject
    CountryReferencePort countryReferencePort;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Config config;

    @BeforeEach
    void resetStubObservations() {
        GeographicReferenceStubResource.resetObservations();
    }

    @Test
    void mapsAContractValidResponseAndForwardsTrustedHeadersOnce() {
        double metricCount = callCount(meterRegistry, "recognized");
        assertTrue(awaitItem(countryReferencePort.isRecognizedCountry(metadata(), "EC")));
        assertEquals(1, GeographicReferenceStubResource.requestCount("EC"));
        assertEquals(metricCount + 1, callCount(meterRegistry, "recognized"));
        String traceparent = GeographicReferenceStubResource.traceparent("EC");
        assertNotNull(traceparent);
        assertTrue(traceparent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]"));
    }

    @Test
    void acceptsValidProviderLatencyWithinTheNormalServiceBudget() {
        assertTrue(awaitItem(countryReferencePort.isRecognizedCountry(metadata(), "SD")));
        assertEquals(1, GeographicReferenceStubResource.requestCount("SD"));
    }

    @Test
    void mapsCountryNotFoundToUnrecognizedWithoutRetry() {
        double metricCount = callCount(meterRegistry, "not-recognized");
        assertFalse(awaitItem(countryReferencePort.isRecognizedCountry(metadata(), "ZZ")));
        assertEquals(1, GeographicReferenceStubResource.requestCount("ZZ"));
        assertEquals(metricCount + 1, callCount(meterRegistry, "not-recognized"));
    }

    @Test
    void mapsServerFailuresToDependencyUnavailableWithoutRetry() {
        double metricCount = callCount(meterRegistry, "unavailable");
        assertDependencyUnavailable(countryReferencePort.isRecognizedCountry(metadata(), "SE"));
        assertEquals(1, GeographicReferenceStubResource.requestCount("SE"));
        assertEquals(metricCount + 1, callCount(meterRegistry, "unavailable"));
    }

    @Test
    void mapsMalformedJsonAndIncompleteResponsesToDependencyUnavailable() {
        assertDependencyUnavailable(countryReferencePort.isRecognizedCountry(metadata(), "MJ"));
        assertDependencyUnavailable(countryReferencePort.isRecognizedCountry(metadata(), "MD"));
        assertDependencyUnavailable(countryReferencePort.isRecognizedCountry(metadata(), "RN"));
        assertDependencyUnavailable(countryReferencePort.isRecognizedCountry(metadata(), "NF"));
        assertDependencyUnavailable(countryReferencePort.isRecognizedCountry(metadata(), "CT"));
        assertDependencyUnavailable(countryReferencePort.isRecognizedCountry(metadata(), "ST"));
        assertDependencyUnavailable(countryReferencePort.isRecognizedCountry(metadata(), "SB"));
        assertDependencyUnavailable(countryReferencePort.isRecognizedCountry(metadata(), "UI"));
        assertEquals(1, GeographicReferenceStubResource.requestCount("MJ"));
        assertEquals(1, GeographicReferenceStubResource.requestCount("MD"));
        assertEquals(1, GeographicReferenceStubResource.requestCount("RN"));
        assertEquals(1, GeographicReferenceStubResource.requestCount("NF"));
        assertEquals(1, GeographicReferenceStubResource.requestCount("CT"));
        assertEquals(1, GeographicReferenceStubResource.requestCount("ST"));
        assertEquals(1, GeographicReferenceStubResource.requestCount("SB"));
        assertEquals(1, GeographicReferenceStubResource.requestCount("UI"));
    }

    @Test
    void mapsConnectionFailuresToDependencyUnavailableWithoutRetry() {
        assertDependencyUnavailable(countryReferencePort.isRecognizedCountry(metadata(), "CF"));
        assertEquals(1, GeographicReferenceStubResource.requestCount("CF"));
    }

    @Test
    void mapsReadTimeoutsToDependencyUnavailableWithinFiniteTimeWithoutRetry() throws Exception {
        var client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(config.getValue("quarkus.rest-client.geographic-reference.url", String.class)))
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .build(GeographicReferenceClient.class);
        var registry = new SimpleMeterRegistry();
        try (var _ = (AutoCloseable) client) {
            var adapter = new GeographicReferenceAdapter(client, objectMapper, registry);
            assertTrue(awaitItem(adapter.isRecognizedCountry(metadata(), "EC")));
            var subscriber = adapter.isRecognizedCountry(metadata(), "TO").subscribe()
                    .withSubscriber(UniAssertSubscriber.create());
            subscriber.awaitFailure(MAXIMUM_WAIT).assertFailed();
            var exception = assertInstanceOf(ApplicationException.class, subscriber.getFailure());
            assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, exception.failure());
            assertInstanceOf(TimeoutException.class, exception.getCause());
            assertEquals(1, GeographicReferenceStubResource.requestCount("TO"));
            assertEquals(1, callCount(registry, "unavailable"));
        } finally {
            registry.close();
        }
    }

    @Test
    void propagatesCancellationToTheReactiveClient() {
        AtomicBoolean cancelled = new AtomicBoolean();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GeographicReferenceClient client = (tenantId, userId, processId, alpha2Code) -> Uni
                .createFrom().<jakarta.ws.rs.core.Response>nothing()
                .onCancellation().invoke(() -> cancelled.set(true));
        CountryReferencePort adapter = new GeographicReferenceAdapter(client, new ObjectMapper(), registry);

        UniAssertSubscriber<Boolean> subscriber = adapter.isRecognizedCountry(metadata(), "EC")
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        subscriber.cancel();

        assertTrue(cancelled.get());
        assertEquals(1, callCount(registry, "cancelled"));
    }

    @Test
    void preservesWrappedCancellationFailures() {
        CompletionException cancellation = new CompletionException(new CancellationException("cancelled"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GeographicReferenceClient client = (tenantId, userId, processId, alpha2Code) -> Uni.createFrom()
                .failure(cancellation);
        CountryReferencePort adapter = new GeographicReferenceAdapter(client, new ObjectMapper(), registry);

        UniAssertSubscriber<Boolean> subscriber = adapter.isRecognizedCountry(metadata(), "EC")
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitFailure(MAXIMUM_WAIT).assertFailedWith(CompletionException.class);
        assertEquals(cancellation, subscriber.getFailure());
        assertEquals(1, callCount(registry, "cancelled"));
    }

    private static RequestMetadata metadata() {
        return new RequestMetadata(
                new TenantId(UUID.fromString(TENANT_ID)),
                USER_ID,
                UUID.fromString(PROCESS_ID));
    }

    private static Boolean awaitItem(Uni<Boolean> result) {
        UniAssertSubscriber<Boolean> subscriber = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create());
        try {
            subscriber.awaitItem(MAXIMUM_WAIT).assertCompleted();
        } catch (AssertionError _) {
            throw new AssertionError("Expected a successful country-reference result", subscriber.getFailure());
        }
        return subscriber.getItem();
    }

    private static void assertDependencyUnavailable(Uni<Boolean> result) {
        UniAssertSubscriber<Boolean> subscriber = result.subscribe()
                .withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitFailure(MAXIMUM_WAIT).assertFailed();

        ApplicationException exception = assertInstanceOf(ApplicationException.class, subscriber.getFailure());
        ApplicationFailure.DependencyUnavailable failure = assertInstanceOf(
                ApplicationFailure.DependencyUnavailable.class,
                exception.failure());
        assertEquals("geographic-reference", failure.dependencyName());
        assertEquals("Dependency unavailable", exception.getMessage());
    }

    private static double callCount(MeterRegistry registry, String outcome) {
        return registry.find("party.registry.geographic.reference.call")
                .tag("outcome", outcome)
                .timers()
                .stream()
                .mapToLong(Timer::count)
                .sum();
    }

    /**
     * Disables persistence services that are unrelated to HTTP adapter tests.
     */
    public static final class GeographicReferenceProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.datasource.devservices.enabled", "false",
                    "quarkus.datasource.health.enabled", "false",
                    "quarkus.flyway.migrate-at-start", "false",
                    "quarkus.hibernate-orm.enabled", "false");
        }
    }
}
