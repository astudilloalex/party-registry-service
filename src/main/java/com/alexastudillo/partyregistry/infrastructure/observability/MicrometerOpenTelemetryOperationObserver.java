package com.alexastudillo.partyregistry.infrastructure.observability;

import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Records bounded operation timers and spans without exposing request or business data.
 */
@ApplicationScoped
public class MicrometerOpenTelemetryOperationObserver implements OperationObservationPort {

    static final String OPERATION_METRIC = "party.registry.operation";
    static final String OPERATION_TAG = "operation";
    static final String OUTCOME_TAG = "outcome";
    static final String OPERATION_ATTRIBUTE = "party.operation";
    static final String OUTCOME_ATTRIBUTE = "party.outcome";

    private static final String INSTRUMENTATION_NAME = "party-registry-service";

    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    /**
     * Creates the telemetry adapter from the service's managed telemetry providers.
     *
     * @param meterRegistry managed Micrometer registry
     * @param openTelemetry managed OpenTelemetry provider
     */
    @Inject
    public MicrometerOpenTelemetryOperationObserver(
            MeterRegistry meterRegistry,
            OpenTelemetry openTelemetry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.tracer = Objects.requireNonNull(openTelemetry, "openTelemetry")
                .getTracer(INSTRUMENTATION_NAME);
    }

    @Override
    public StartedObservation start(
            RequestMetadata requestMetadata,
            ObservedOperation operation) {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(operation, OPERATION_TAG);
        Timer.Sample sample = Timer.start(meterRegistry);
        String operationLabel = operationLabel(operation);
        Span span = tracer.spanBuilder("party-registry." + operationLabel)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        return new TelemetryObservation(sample, span, operationLabel);
    }

    static String operationLabel(ObservedOperation operation) {
        return switch (operation) {
            case APPLICATION_NATURAL_PERSON_REGISTRATION ->
                "application.natural-person-registration";
            case APPLICATION_LEGAL_ENTITY_REGISTRATION ->
                "application.legal-entity-registration";
            case APPLICATION_ADDITIONAL_IDENTIFIER_REGISTRATION ->
                "application.additional-identifier-registration";
            case APPLICATION_PARTY_LIST -> "application.party-list";
            case APPLICATION_PARTY_RETRIEVAL -> "application.party-retrieval";
            case APPLICATION_PARTY_PATCH -> "application.party-patch";
            case APPLICATION_PARTY_ACTIVATION -> "application.party-activation";
            case APPLICATION_PARTY_DEACTIVATION -> "application.party-deactivation";
            case APPLICATION_PARTY_ARCHIVAL -> "application.party-archival";
            case TRANSACTION_NATURAL_PERSON_REGISTRATION ->
                "transaction.natural-person-registration";
            case TRANSACTION_LEGAL_ENTITY_REGISTRATION ->
                "transaction.legal-entity-registration";
            case TRANSACTION_ADDITIONAL_IDENTIFIER_REGISTRATION ->
                "transaction.additional-identifier-registration";
            case TRANSACTION_PARTY_ACTIVATION -> "transaction.party-activation";
            case TRANSACTION_PARTY_MUTATION -> "transaction.party-mutation";
        };
    }

    static String outcomeLabel(OperationOutcome outcome) {
        return switch (outcome) {
            case RETRIEVED -> "retrieved";
            case CREATED -> "created";
            case REPLAYED -> "replayed";
            case ACTIVATED -> "activated";
            case DEACTIVATED -> "deactivated";
            case ARCHIVED -> "archived";
            case APPLIED -> "applied";
            case VALIDATION_FAILED -> "validation-failed";
            case CONFLICT -> "conflict";
            case IDENTIFIER_CONFLICT -> "identifier-conflict";
            case NOT_FOUND -> "not-found";
            case PRECONDITION_FAILED -> "precondition-failed";
            case DEPENDENCY_UNAVAILABLE -> "dependency-unavailable";
            case CANCELLED -> "cancelled";
            case INTERNAL_FAILURE -> "internal-failure";
        };
    }

    /**
     * Completes one timer and span defensively when the application terminates.
     */
    private final class TelemetryObservation implements StartedObservation {

        private final Timer.Sample sample;
        private final Span span;
        private final String operation;
        private final AtomicBoolean completed = new AtomicBoolean();

        private TelemetryObservation(Timer.Sample sample, Span span, String operation) {
            this.sample = sample;
            this.span = span;
            this.operation = operation;
        }

        @Override
        public void complete(OperationOutcome outcome) {
            Objects.requireNonNull(outcome, OUTCOME_TAG);
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            String outcomeValue = outcomeLabel(outcome);
            try {
                sample.stop(Timer.builder(OPERATION_METRIC)
                        .tag(OPERATION_TAG, operation)
                        .tag(OUTCOME_TAG, outcomeValue)
                        .register(meterRegistry));
            } finally {
                span.setAttribute(OPERATION_ATTRIBUTE, operation);
                span.setAttribute(OUTCOME_ATTRIBUTE, outcomeValue);
                span.end();
            }
        }
    }
}
