package com.alexastudillo.partyregistry.api.observability;

import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Locale;
import java.util.Set;
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
    static final String MUTATION_METRIC = "party.registry.http.mutation";
    static final String NATIONALITY_MUTATION_METRIC = "party.registry.http.nationality.mutation";
    static final String UNMATCHED_OPERATION = "unmatched";
    private static final String UNSUPPORTED_OPERATION = "unsupported";
    private static final String PATCH_METHOD = "PATCH";

    static final String OPERATION_TAG = "operation";
    static final String OUTCOME_TAG = "outcome";
    static final String CODE_TAG = "code";
    static final String CONFLICT_OUTCOME = "conflict";

    private static final String NATURAL_PERSON_PATH = "/v1/natural-person";
    private static final String NATURAL_PERSON_ITEM_PREFIX = NATURAL_PERSON_PATH + "/";
    private static final String LEGAL_ENTITY_PATH = "/v1/legal-entity";
    private static final String LEGAL_ENTITY_ITEM_PREFIX = LEGAL_ENTITY_PATH + "/";
    private static final String PARTY_PATH = "/v1/parties";
    private static final String PARTY_ITEM_PREFIX = PARTY_PATH + "/";
    private static final String IDENTIFIERS_SUFFIX = "/identifiers";
    private static final String ACTIVATE_SUFFIX = "/activate";
    private static final String NATIONALITIES_SEGMENT = "nationalities";
    private static final Set<String> ROOT_MUTATIONS = Set.of("patch-party", "activate", "deactivate", "archive");

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
     * @return stable operation label, {@code unsupported} for a recognized route's other methods, or {@code unmatched}
     */
    public String operationName(String method, String path) {
        String nationalityOperation = nationalityOperationName(method, path);
        if (!UNMATCHED_OPERATION.equals(nationalityOperation)) {
            return nationalityOperation;
        }
        String rootOperation = rootOperationName(method, path);
        if (!UNMATCHED_OPERATION.equals(rootOperation)) {
            return rootOperation;
        }
        if (NATURAL_PERSON_PATH.equals(path) && "POST".equals(method)) {
            return "create";
        }
        if (LEGAL_ENTITY_PATH.equals(path) && "POST".equals(method)) {
            return "create-legal-entity";
        }
        if (path.startsWith(LEGAL_ENTITY_ITEM_PREFIX)
                && path.length() > LEGAL_ENTITY_ITEM_PREFIX.length()
                && path.indexOf('/', LEGAL_ENTITY_ITEM_PREFIX.length()) < 0) {
            return switch (method) {
                case "GET" -> "retrieve-legal-entity";
                case "PUT" -> "replace-legal-entity";
                case PATCH_METHOD -> "patch-legal-entity";
                default -> UNSUPPORTED_OPERATION;
            };
        }
        if ("POST".equals(method) && isPartyOperationPath(path, IDENTIFIERS_SUFFIX)) {
            return "register-identifier";
        }
        if (path.startsWith(NATURAL_PERSON_ITEM_PREFIX)
                && path.indexOf('/', NATURAL_PERSON_ITEM_PREFIX.length()) < 0) {
            return switch (method) {
                case "GET" -> "retrieve";
                case "PUT" -> "replace";
                case PATCH_METHOD -> "patch";
                default -> UNSUPPORTED_OPERATION;
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

        if (status == 400 || status == 422) {
            meterRegistry.counter(VALIDATION_METRIC, OPERATION_TAG, operation, CODE_TAG, code)
                    .increment();
        }
        if ("create".equals(operation) || "create-legal-entity".equals(operation)) {
            if (idempotencyOutcome != null) {
                meterRegistry.counter(
                        IDEMPOTENCY_METRIC,
                        OUTCOME_TAG, idempotencyOutcome.name().toLowerCase(Locale.ROOT))
                        .increment();
            } else if ("idempotency-key-conflict".equals(code)) {
                meterRegistry.counter(IDEMPOTENCY_METRIC, OUTCOME_TAG, CONFLICT_OUTCOME)
                        .increment();
            }
        }
        if (("replace".equals(operation) || "patch".equals(operation) || ROOT_MUTATIONS.contains(operation)
                || "replace-legal-entity".equals(operation) || "patch-legal-entity".equals(operation))
                && status == 412) {
            meterRegistry.counter(OPTIMISTIC_CONFLICT_METRIC, OPERATION_TAG, operation)
                    .increment();
        }

        Span.current()
                .setAttribute("party.operation", operation)
                .setAttribute("party.outcome", outcome)
                .setAttribute("party.response.code", code);
    }

    /** Records accepted or rejected root mutation outcomes without retaining keys, Party data, or raw request values. */
    public void recordMutationCompletion(String operation, int status, String code, PartyMutationOutcome.Disposition disposition) {
        if (!ROOT_MUTATIONS.contains(operation)) {
            return;
        }
        String outcome;
        if (status < 400 && disposition != null) {
            outcome = disposition.name().toLowerCase(Locale.ROOT);
        } else if (status == 409 || status == 412) {
            outcome = CONFLICT_OUTCOME;
        } else {
            return;
        }
        meterRegistry.counter(MUTATION_METRIC, OPERATION_TAG, operation, OUTCOME_TAG, outcome, CODE_TAG, code).increment();
        Span.current().setAttribute("party.mutation.outcome", outcome);
    }

    /** Records only keyed nationality create/set-primary dispositions and bounded conflict outcomes. */
    public void recordNationalityCompletion(String operation, int status, String code,
            NationalityMutationOutcome.Disposition disposition, boolean keyed) {
        if (!keyed || (!"create-nationality".equals(operation) && !"set-primary-nationality".equals(operation))) {
            return;
        }
        String outcome;
        if (status < 400 && disposition != null) {
            outcome = disposition.name().toLowerCase(Locale.ROOT);
        } else if (status == 409) {
            outcome = CONFLICT_OUTCOME;
        } else {
            return;
        }
        meterRegistry.counter(NATIONALITY_MUTATION_METRIC, OPERATION_TAG, operation, OUTCOME_TAG, outcome,
                CODE_TAG, code).increment();
        Span.current().setAttribute("party.nationality.mutation.outcome", outcome);
    }

    private static String nationalityOperationName(String method, String path) {
        String[] segments = path.split("/", -1);
        if (segments.length < 5 || !segments[0].isEmpty() || !"v1".equals(segments[1])
                || !"parties".equals(segments[2]) || segments[3].isEmpty()
                || !NATIONALITIES_SEGMENT.equals(segments[4])) {
            return UNMATCHED_OPERATION;
        }
        if (segments.length == 5) {
            return switch (method) {
                case "POST" -> "create-nationality";
                case "GET" -> "list-nationalities";
                default -> UNSUPPORTED_OPERATION;
            };
        }
        if (segments[5].isEmpty()) {
            return UNMATCHED_OPERATION;
        }
        if (segments.length == 6) {
            return switch (method) {
                case "GET" -> "retrieve-nationality";
                case PATCH_METHOD -> "patch-nationality";
                default -> UNSUPPORTED_OPERATION;
            };
        }
        if (segments.length == 7 && "set-primary".equals(segments[6])) {
            return "POST".equals(method) ? "set-primary-nationality" : UNSUPPORTED_OPERATION;
        }
        return UNMATCHED_OPERATION;
    }

    private static String rootOperationName(String method, String path) {
        if (PARTY_PATH.equals(path)) {
            return "GET".equals(method) ? "list-parties" : UNSUPPORTED_OPERATION;
        }
        if (isPartyOperationPath(path, "")) {
            return switch (method) {
                case "GET" -> "retrieve-party";
                case PATCH_METHOD -> "patch-party";
                default -> UNSUPPORTED_OPERATION;
            };
        }
        for (String action : new String[] {ACTIVATE_SUFFIX, "/deactivate", "/archive"}) {
            if (isPartyOperationPath(path, action)) {
                return "POST".equals(method) ? action.substring(1) : UNSUPPORTED_OPERATION;
            }
        }
        return UNMATCHED_OPERATION;
    }

    private static boolean isPartyOperationPath(String path, String suffix) {
        if (!path.startsWith(PARTY_ITEM_PREFIX) || !path.endsWith(suffix)) {
            return false;
        }
        String partyId = path.substring(PARTY_ITEM_PREFIX.length(), path.length() - suffix.length());
        return !partyId.isEmpty() && partyId.indexOf('/') < 0;
    }
}
