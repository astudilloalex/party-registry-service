package com.alexastudillo.partyregistry.api.observability;

import com.alexastudillo.partyregistry.application.model.IdempotentCreationOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Records bounded operational telemetry for natural-person HTTP requests.
 */
@ApplicationScoped
public class NaturalPersonObservability {

    static final String OPERATION_METRIC = "party.registry.natural.person.operation";
    static final String VALIDATION_METRIC = "party.registry.natural.person.validation.failures";
    static final String IDEMPOTENCY_METRIC = "party.registry.natural.person.idempotency";
    static final String OPTIMISTIC_CONFLICT_METRIC = "party.registry.natural.person.optimistic.conflicts";
    static final String UNMATCHED_OPERATION = "unmatched";

    static final String OPERATION_TAG = "operation";
    static final String OUTCOME_TAG = "outcome";
    static final String CODE_TAG = "code";

    private static final String NATURAL_PERSON_PATH = "/v1/natural-person";
    private static final String NATURAL_PERSON_ITEM_PREFIX = NATURAL_PERSON_PATH + "/";

    private final MeterRegistry meterRegistry;

    public NaturalPersonObservability(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Resolves a low-cardinality operation label without retaining path
     * identifiers.
     *
     * @param method HTTP method
     * @param path   normalized request path
     * @return stable operation label or `unmatched`
     */
    public String operationName(String method, String path) {
        if (NATURAL_PERSON_PATH.equals(path) && "POST".equals(method)) {
            return "create";
        }
        if (path.startsWith(NATURAL_PERSON_ITEM_PREFIX)
                && path.indexOf('/', NATURAL_PERSON_ITEM_PREFIX.length()) < 0) {
            return switch (method) {
                case "GET" -> "retrieve";
                case "PUT" -> "replace";
                case "PATCH" -> "patch";
                default -> "unsupported";
            };
        }
        return UNMATCHED_OPERATION;
    }

    /**
     * Records final request telemetry using only bounded operational labels.
     *
     * @param operation          stable operation name
     * @param status             HTTP response status
     * @param code               stable response code
     * @param durationNanos      elapsed request duration
     * @param idempotencyOutcome create outcome when available
     */
    public void recordCompletion(
            String operation,
            int status,
            String code,
            long durationNanos,
            IdempotentCreationOutcome idempotencyOutcome) {
        if (UNMATCHED_OPERATION.equals(operation)) {
            return;
        }

        String outcome = status < 400 ? "success" : "failure";
        meterRegistry.timer(
                OPERATION_METRIC,
                OPERATION_TAG, operation,
                OUTCOME_TAG, outcome,
                CODE_TAG, code)
                .record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);

        if ("bad-request".equals(code) || "unprocessable-entity".equals(code)) {
            meterRegistry.counter(VALIDATION_METRIC, OPERATION_TAG, operation, CODE_TAG, code)
                    .increment();
        }
        if ("create".equals(operation)) {
            if (idempotencyOutcome != null) {
                meterRegistry.counter(
                        IDEMPOTENCY_METRIC,
                        OUTCOME_TAG, idempotencyOutcome.name().toLowerCase(Locale.ROOT))
                        .increment();
            } else if ("conflict".equals(code)) {
                meterRegistry.counter(IDEMPOTENCY_METRIC, OUTCOME_TAG, "conflict")
                        .increment();
            }
        }
        if (("replace".equals(operation) || "patch".equals(operation))
                && "precondition-failed".equals(code)) {
            meterRegistry.counter(OPTIMISTIC_CONFLICT_METRIC, OPERATION_TAG, operation)
                    .increment();
        }

        Span.current()
                .setAttribute("party.operation", operation)
                .setAttribute("party.outcome", outcome)
                .setAttribute("party.response.code", code);
    }
}
