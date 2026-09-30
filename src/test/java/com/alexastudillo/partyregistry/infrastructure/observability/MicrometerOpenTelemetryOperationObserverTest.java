package com.alexastudillo.partyregistry.infrastructure.observability;

import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies bounded Micrometer tags and OpenTelemetry span attributes.
 */
class MicrometerOpenTelemetryOperationObserverTest {

    private static final RequestMetadata METADATA = new RequestMetadata(
            new TenantId(UUID.fromString("01991e8b-e000-7000-8000-000000000001")),
            "sensitive-request-user",
            UUID.fromString("01991e8b-e000-7000-8000-000000000002"));

    @Test
    void emitsOnlyBoundedOperationAndOutcomeTelemetry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CollectingSpanExporter exporter = new CollectingSpanExporter();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        var observer = new MicrometerOpenTelemetryOperationObserver(registry, openTelemetry);

        OperationObservationPort.StartedObservation observation = observer.start(
                METADATA,
                ObservedOperation.APPLICATION_NATURAL_PERSON_REGISTRATION);

        assertTrue(registry.find(MicrometerOpenTelemetryOperationObserver.OPERATION_METRIC)
                .timers().isEmpty());
        assertTrue(exporter.spans().isEmpty());

        observation.complete(OperationOutcome.DEPENDENCY_UNAVAILABLE);
        observation.complete(OperationOutcome.CREATED);

        Timer timer = registry.get(MicrometerOpenTelemetryOperationObserver.OPERATION_METRIC)
                .tags(
                        MicrometerOpenTelemetryOperationObserver.OPERATION_TAG,
                        "application.natural-person-registration",
                        MicrometerOpenTelemetryOperationObserver.OUTCOME_TAG,
                        "dependency-unavailable")
                .timer();
        assertEquals(1L, timer.count());
        assertEquals(Set.of(
                        MicrometerOpenTelemetryOperationObserver.OPERATION_TAG,
                        MicrometerOpenTelemetryOperationObserver.OUTCOME_TAG),
                timer.getId().getTags().stream().map(Tag::getKey)
                        .collect(java.util.stream.Collectors.toSet()));

        SpanData span = exporter.spans().getFirst();
        assertEquals("party-registry.application.natural-person-registration", span.getName());
        assertEquals(Set.of(
                        MicrometerOpenTelemetryOperationObserver.OPERATION_ATTRIBUTE,
                        MicrometerOpenTelemetryOperationObserver.OUTCOME_ATTRIBUTE),
                span.getAttributes().asMap().keySet().stream()
                        .map(AttributeKey::getKey)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(
                "application.natural-person-registration",
                span.getAttributes().get(AttributeKey.stringKey(
                        MicrometerOpenTelemetryOperationObserver.OPERATION_ATTRIBUTE)));
        assertEquals(
                "dependency-unavailable",
                span.getAttributes().get(AttributeKey.stringKey(
                        MicrometerOpenTelemetryOperationObserver.OUTCOME_ATTRIBUTE)));

        String labels = timer.getId().getTags() + span.getName() + span.getAttributes();
        assertFalse(labels.contains(METADATA.tenantId().value().toString()));
        assertFalse(labels.contains(METADATA.userId()));
        assertFalse(labels.contains(METADATA.processId().toString()));
        assertEquals(1, exporter.spans().size());

        tracerProvider.close();
        registry.close();
    }

    @Test
    void mapsEveryClosedEnumValueToOneDistinctBoundedLabel() {
        assertEquals(
                ObservedOperation.values().length,
                java.util.Arrays.stream(ObservedOperation.values())
                        .map(MicrometerOpenTelemetryOperationObserver::operationLabel)
                        .distinct()
                        .count());
        assertEquals(
                OperationOutcome.values().length,
                java.util.Arrays.stream(OperationOutcome.values())
                        .map(MicrometerOpenTelemetryOperationObserver::outcomeLabel)
                        .distinct()
                        .count());
    }

    /**
     * Captures completed span data synchronously for isolated telemetry tests.
     */
    private static final class CollectingSpanExporter implements SpanExporter {

        private final List<SpanData> spans = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            this.spans.addAll(spans);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        private List<SpanData> spans() {
            return List.copyOf(spans);
        }
    }
}
