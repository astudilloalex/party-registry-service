package com.alexastudillo.partyregistry.api.observability;

import com.alexastudillo.partyregistry.api.model.request.NaturalPersonCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonPatchRequest;
import com.alexastudillo.partyregistry.api.model.request.NaturalPersonPutRequest;
import com.alexastudillo.partyregistry.api.model.request.LegalEntityPutRequest;
import com.alexastudillo.partyregistry.api.model.request.LegalEntityPatchRequest;
import com.alexastudillo.partyregistry.api.model.response.LegalEntityResponse;
import com.alexastudillo.partyregistry.api.resource.LegalEntityResource;
import com.alexastudillo.api.response.contract.ApiResponse;
import com.alexastudillo.partyregistry.api.resource.NaturalPersonResource;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.infrastructure.integration.geographic.adapter.GeographicReferenceAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.ws.rs.core.HttpHeaders;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Verifies bounded natural-person metrics and explicit operation spans.
 */
class PartyHttpObservabilityTest {

    @Test
    void recordsBoundedOperationAndBusinessOutcomeMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PartyHttpObservability observability = new PartyHttpObservability(registry);

        observability.recordCompletion("create", 201, "successful", 10, PartyRegistrationOutcome.CREATED);
        observability.recordCompletion("create", 201, "successful", 10, PartyRegistrationOutcome.REPLAYED);
        observability.recordCompletion(
                "create-legal-entity", 201, "successful", 10, PartyRegistrationOutcome.CREATED);
        observability.recordCompletion("create", 409, "idempotency-key-conflict", 10, null);
        observability.recordCompletion("patch", 400, "bad-request", 10, null);
        observability.recordCompletion("replace", 412, "expected-version-mismatch", 10, null);
        observability.recordCompletion("activate", 412, "stale-party-version", 10, null);
        observability.recordCompletion("unmatched", 404, "not-found", 10, null);

        assertEquals(2, timerCount(registry, "create", "success", "successful"));
        assertEquals(1, timerCount(registry, "create", "failure", "idempotency-key-conflict"));
        assertEquals(1, counterCount(
                registry,
                PartyHttpObservability.VALIDATION_METRIC,
                PartyHttpObservability.OPERATION_TAG, "patch",
                PartyHttpObservability.CODE_TAG, "bad-request"));
        assertEquals(2, counterCount(
                registry,
                PartyHttpObservability.IDEMPOTENCY_METRIC,
                PartyHttpObservability.OUTCOME_TAG, "created"));
        assertEquals(1, counterCount(
                registry,
                PartyHttpObservability.IDEMPOTENCY_METRIC,
                PartyHttpObservability.OUTCOME_TAG, "replayed"));
        assertEquals(1, counterCount(
                registry,
                PartyHttpObservability.IDEMPOTENCY_METRIC,
                PartyHttpObservability.OUTCOME_TAG, "conflict"));
        assertEquals(1, counterCount(
                registry,
                PartyHttpObservability.OPTIMISTIC_CONFLICT_METRIC,
                PartyHttpObservability.OPERATION_TAG, "replace"));
        assertEquals(1, counterCount(
                registry,
                PartyHttpObservability.OPTIMISTIC_CONFLICT_METRIC,
                PartyHttpObservability.OPERATION_TAG, "activate"));
        assertEquals(0, registry.find(PartyHttpObservability.OPERATION_METRIC)
                .tag(PartyHttpObservability.OPERATION_TAG, "unmatched")
                .timers()
                .size());
    }

    @Test
    void countsSpecificHeaderValidationCodesWithoutLosingOperationMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            PartyHttpObservability observability = new PartyHttpObservability(registry);
            for (String code : List.of("process-id-required", "process-id-invalid", "tenant-id-duplicated",
                    "user-id-blank", "user-id-unsafe")) {
                observability.recordCompletion("create", 400, code, 10, null);
                assertEquals(1, counterCount(registry, PartyHttpObservability.VALIDATION_METRIC,
                        PartyHttpObservability.OPERATION_TAG, "create", PartyHttpObservability.CODE_TAG, code));
                assertEquals(1, timerCount(registry, "create", "failure", code));
            }
        } finally {
            registry.close();
        }
    }

    @Test
    void resolvesOnlyStableOperationNames() {
        PartyHttpObservability observability = new PartyHttpObservability(new SimpleMeterRegistry());

        assertEquals("create", observability.operationName("POST", "/v1/natural-person"));
        assertEquals("retrieve", observability.operationName("GET", "/v1/natural-person/party-id"));
        assertEquals("replace", observability.operationName("PUT", "/v1/natural-person/party-id"));
        assertEquals("patch", observability.operationName("PATCH", "/v1/natural-person/party-id"));
        assertEquals("create-legal-entity", observability.operationName("POST", "/v1/legal-entity"));
        assertEquals(
                "register-identifier",
                observability.operationName("POST", "/v1/parties/party-id/identifiers"));
        assertEquals("activate", observability.operationName("POST", "/v1/parties/party-id/activate"));
        assertEquals("unsupported", observability.operationName("DELETE", "/v1/natural-person/party-id"));
        assertEquals("retrieve-party", observability.operationName("GET", "/v1/parties/party-id"));
        assertEquals("retrieve-legal-entity", observability.operationName("GET", "/v1/legal-entity/party-id"));
        assertEquals("replace-legal-entity", observability.operationName("PUT", "/v1/legal-entity/party-id"));
        assertEquals("patch-legal-entity", observability.operationName("PATCH", "/v1/legal-entity/party-id"));
        assertEquals("unmatched", observability.operationName("GET", "/v1/legal-entity/"));
        assertEquals("unmatched", observability.operationName("GET", "/v1/legal-entity/id/extra"));
    }

    @Test
    void countsLegalSpecificFailuresWithBoundedLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            PartyHttpObservability observability = new PartyHttpObservability(registry);
            for (String operation : List.of("replace-legal-entity", "patch-legal-entity")) {
                observability.recordCompletion(operation, 412, "expected-version-mismatch", 10, null);
                assertEquals(1, counterCount(registry, PartyHttpObservability.OPTIMISTIC_CONFLICT_METRIC,
                        PartyHttpObservability.OPERATION_TAG, operation));
                for (String code : List.of("incorporation-date-in-future", "dissolution-date-in-future",
                        "dissolution-before-incorporation", "unrecognized-incorporation-country")) {
                    observability.recordCompletion(operation, 422, code, 10, null);
                    assertEquals(1, counterCount(registry, PartyHttpObservability.VALIDATION_METRIC,
                            PartyHttpObservability.OPERATION_TAG, operation, PartyHttpObservability.CODE_TAG, code));
                }
            }
        } finally {
            registry.close();
        }
    }

    @Test
    void legalResourcesDeclareTypedEnvelopesAndSpecificSpans() throws ReflectiveOperationException {
        List<Method> methods = List.of(
                LegalEntityResource.class.getMethod("getLegalEntity", String.class),
                LegalEntityResource.class.getMethod("replaceLegalEntity", String.class, LegalEntityPutRequest.class, HttpHeaders.class),
                LegalEntityResource.class.getMethod("patchLegalEntity", String.class, LegalEntityPatchRequest.class, HttpHeaders.class));
        List<String> spans = List.of("legal-entity.retrieve", "legal-entity.replace", "legal-entity.patch");
        for (int index = 0; index < methods.size(); index++) {
            Method method = methods.get(index);
            assertEquals(spans.get(index), method.getAnnotation(WithSpan.class).value());
            ParameterizedType uni = assertInstanceOf(ParameterizedType.class, method.getGenericReturnType());
            assertEquals(io.smallrye.mutiny.Uni.class, uni.getRawType());
            ParameterizedType rest = assertInstanceOf(ParameterizedType.class, uni.getActualTypeArguments()[0]);
            assertEquals(org.jboss.resteasy.reactive.RestResponse.class, rest.getRawType());
            ParameterizedType envelope = assertInstanceOf(ParameterizedType.class, rest.getActualTypeArguments()[0]);
            assertEquals(ApiResponse.class, envelope.getRawType());
            assertEquals(LegalEntityResponse.class, envelope.getActualTypeArguments()[0]);
        }
    }

    @Test
    void declaresExplicitSpansAtOuterReactiveBoundaries() throws ReflectiveOperationException {
        assertSpan("createNaturalPerson", "natural-person.create", NaturalPersonCreateRequest.class,
                HttpHeaders.class);
        assertSpan("getNaturalPerson", "natural-person.retrieve", String.class);
        assertSpan(
                "replaceNaturalPerson",
                "natural-person.replace",
                String.class,
                NaturalPersonPutRequest.class,
                HttpHeaders.class);
        assertSpan(
                "patchNaturalPerson",
                "natural-person.patch",
                String.class,
                NaturalPersonPatchRequest.class,
                HttpHeaders.class);

        Method geographicCall = GeographicReferenceAdapter.class.getMethod(
                "isRecognizedCountry",
                RequestMetadata.class,
                String.class);
        WithSpan annotation = geographicCall.getAnnotation(WithSpan.class);
        assertNotNull(annotation);
        assertEquals("geographic-reference.validate-country", annotation.value());
    }

    private static void assertSpan(
            String methodName,
            String expectedSpanName,
            Class<?>... parameterTypes) throws ReflectiveOperationException {
        Method method = NaturalPersonResource.class.getMethod(methodName, parameterTypes);
        WithSpan annotation = method.getAnnotation(WithSpan.class);
        assertNotNull(annotation);
        assertEquals(expectedSpanName, annotation.value());
    }

    private static double timerCount(
            SimpleMeterRegistry registry,
            String operation,
            String outcome,
            String code) {
        return registry.get(PartyHttpObservability.OPERATION_METRIC)
                .tags(
                        PartyHttpObservability.OPERATION_TAG, operation,
                        PartyHttpObservability.OUTCOME_TAG, outcome,
                        PartyHttpObservability.CODE_TAG, code)
                .timer()
                .count();
    }

    private static double counterCount(
            SimpleMeterRegistry registry,
            String metric,
            String... tags) {
        return registry.get(metric).tags(tags).counter().count();
    }
}
