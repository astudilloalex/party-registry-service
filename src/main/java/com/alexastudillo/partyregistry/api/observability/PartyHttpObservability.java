package com.alexastudillo.partyregistry.api.observability;

import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Records bounded operational telemetry for implemented Party HTTP requests.
 */
@ApplicationScoped
public class PartyHttpObservability {

    static final String OPERATION_METRIC = "party.registry.http.operation";
    static final String VALIDATION_METRIC = "party.registry.http.validation.failures";
    static final String IDEMPOTENCY_METRIC = "party.registry.http.idempotency";
    static final String OPTIMISTIC_CONFLICT_METRIC = "party.registry.http.optimistic.conflicts";
    static final String UNMATCHED_OPERATION = "unmatched";

    static final String OPERATION_TAG = "operation";
    static final String OUTCOME_TAG = "outcome";
    static final String CODE_TAG = "code";

    private static final String NATURAL_PERSON_PATH = "/v1/natural-person";
    private static final String NATURAL_PERSON_ITEM_PREFIX = NATURAL_PERSON_PATH + "/";
    private static final String LEGAL_ENTITY_PATH = "/v1/legal-entity";
    private static final String PARTY_ITEM_PREFIX = "/v1/parties/";
    private static final String IDENTIFIERS_SUFFIX = "/identifiers";
    private static final String ACTIVATE_SUFFIX = "/activate";

    private final MeterRegistry meterRegistry;

    public PartyHttpObservability(MeterRegistry meterRegistry) {
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
        if (LEGAL_ENTITY_PATH.equals(path) && "POST".equals(method)) {
            return "create-legal-entity";
        }
        if ("POST".equals(method) && isPartyOperationPath(path, IDENTIFIERS_SUFFIX)) {
            return "register-identifier";
        }
        if ("POST".equals(method) && isPartyOperationPath(path, ACTIVATE_SUFFIX)) {
            return "activate";
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
            PartyRegistrationOutcome idempotencyOutcome) {
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
        if ("create".equals(operation) || "create-legal-entity".equals(operation)) {
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
        if (("replace".equals(operation) || "patch".equals(operation) || "activate".equals(operation))
                && "precondition-failed".equals(code)) {
            meterRegistry.counter(OPTIMISTIC_CONFLICT_METRIC, OPERATION_TAG, operation)
                    .increment();
        }

        Span.current()
                .setAttribute("party.operation", operation)
                .setAttribute("party.outcome", outcome)
                .setAttribute("party.response.code", code);
    }

    private static boolean isPartyOperationPath(String path, String suffix) {
        if (!path.startsWith(PARTY_ITEM_PREFIX) || !path.endsWith(suffix)) {
            return false;
        }
        String partyId = path.substring(PARTY_ITEM_PREFIX.length(), path.length() - suffix.length());
        return !partyId.isEmpty() && partyId.indexOf('/') < 0;
    }
}
