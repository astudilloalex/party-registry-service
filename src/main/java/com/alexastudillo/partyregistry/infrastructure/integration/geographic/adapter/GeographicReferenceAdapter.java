package com.alexastudillo.partyregistry.infrastructure.integration.geographic.adapter;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import com.alexastudillo.partyregistry.infrastructure.integration.geographic.client.GeographicReferenceClient;
import com.alexastudillo.partyregistry.infrastructure.integration.geographic.dto.CountryReferenceResponse;
import com.alexastudillo.partyregistry.infrastructure.integration.geographic.dto.CountryReferenceResponse.CountryData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adapts Geographic Reference HTTP outcomes to the country reference output
 * port.
 */
@ApplicationScoped
public class GeographicReferenceAdapter implements CountryReferencePort {

    private static final String DEPENDENCY_NAME = "geographic-reference";
    static final String CALL_METRIC = "party.registry.geographic.reference.call";
    private static final String PROCESS_ID_HEADER = "Process-Id";
    private static final String SUCCESSFUL = "successful";
    private static final String COUNTRY_NOT_FOUND = "country-not-found";
    private static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final String STATUS_FIELD = "status";
    private static final Set<String> SUCCESS_FIELDS = Set.of(STATUS_FIELD, "code", "data");
    private static final Set<String> ERROR_FIELDS = Set.of(STATUS_FIELD, "code");
    private static final Set<String> COUNTRY_FIELDS = Set.of(
            "id",
            "alpha2Code",
            "alpha3Code",
            "numericCode",
            "defaultName",
            "officialName",
            "independent",
            STATUS_FIELD,
            "validFrom",
            "validUntil",
            "sourceAuthority",
            "sourceReference",
            "sourceRevision",
            "createdAt",
            "createdBy",
            "updatedAt",
            "updatedBy",
            "version");

    private final GeographicReferenceClient client;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private static final System.Logger LOGGER = System.getLogger(GeographicReferenceAdapter.class.getName());

    @Inject
    public GeographicReferenceAdapter(
            @RestClient GeographicReferenceClient client,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @WithSpan("geographic-reference.validate-country")
    public Uni<Boolean> isRecognizedCountry(RequestMetadata requestMetadata, String alpha2Code) {
        return Uni.createFrom().deferred(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            AtomicBoolean recorded = new AtomicBoolean();
            String processId = requestMetadata.processId().toString();
            return client.findByAlpha2Code(
                    requestMetadata.tenantId().value().toString(),
                    requestMetadata.userId(),
                    processId,
                    alpha2Code)
                    .map(response -> translateAndClose(response, alpha2Code, processId))
                    .onFailure(this::isTechnicalFailure)
                    .transform(this::dependencyUnavailable)
                    .onItem().invoke(recognized -> recordCall(
                            sample,
                            recorded,
                            Boolean.TRUE.equals(recognized) ? "recognized" : "not-recognized"))
                    .onFailure().invoke(failure -> recordCall(
                            sample,
                            recorded,
                            containsCancellation(failure) ? "cancelled" : "unavailable"))
                    .onCancellation().invoke(() -> recordCall(sample, recorded, "cancelled"));
        });
    }

    private void recordCall(Timer.Sample sample, AtomicBoolean recorded, String outcome) {
        if (!recorded.compareAndSet(false, true)) {
            return;
        }
        sample.stop(Timer.builder(CALL_METRIC)
                .tag("outcome", outcome)
                .register(meterRegistry));
        Span.current().setAttribute("geographic.reference.outcome", outcome);
        LOGGER.log(
                System.Logger.Level.INFO,
                "Geographic Reference call completed outcome={0}",
                outcome);
    }

    private boolean translateAndClose(
            Response response,
            String alpha2Code,
            String processId) {
        if (response == null) {
            throw malformedResponse();
        }
        try (response) {
            return translate(response, alpha2Code, processId);
        }
    }

    private boolean translate(
            Response response,
            String alpha2Code,
            String processId) {
        if (!processId.equals(response.getHeaderString(PROCESS_ID_HEADER))) {
            throw malformedResponse();
        }

        return switch (response.getStatus()) {
            case 200 -> {
                CountryReferenceResponse body = responseBody(response);
                if (!isValidSuccess(body, alpha2Code)) {
                    throw malformedResponse();
                }
                yield true;
            }
            case 404 -> {
                CountryReferenceResponse body = responseBody(response);
                if (!isValidNotFound(body)) {
                    throw malformedResponse();
                }
                yield false;
            }
            default -> throw dependencyUnavailable(
                    new IllegalStateException("Geographic Reference Service returned an unusable status"));
        };
    }

    private CountryReferenceResponse responseBody(Response response) {
        MediaType mediaType = response.getMediaType();
        if (mediaType == null || !MediaType.APPLICATION_JSON_TYPE.isCompatible(mediaType)) {
            throw malformedResponse();
        }

        try {
            JsonNode payload = objectMapper.readTree(response.readEntity(String.class));
            boolean validShape = response.getStatus() == 200
                    ? hasExactFields(payload, SUCCESS_FIELDS)
                            && hasExactFields(payload.get("data"), COUNTRY_FIELDS)
                            && hasValidSuccessTypes(payload)
                    : hasExactFields(payload, ERROR_FIELDS)
                            && hasValidErrorTypes(payload);
            if (!validShape) {
                throw malformedResponse();
            }
            return objectMapper.treeToValue(payload, CountryReferenceResponse.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw dependencyUnavailable(exception);
        }
    }

    private boolean isValidSuccess(CountryReferenceResponse response, String alpha2Code) {
        if (response == null
                || !Integer.valueOf(200).equals(response.status())
                || !SUCCESSFUL.equals(response.code())) {
            return false;
        }

        CountryData data = response.data();
        return data != null
                && data.id() != null
                && alpha2Code.equals(data.alpha2Code())
                && matches(data.alpha2Code(), "[A-Z]{2}")
                && matches(data.alpha3Code(), "[A-Z]{3}")
                && matches(data.numericCode(), "[0-9]{3}")
                && hasLength(data.defaultName(), 150)
                && hasLength(data.officialName(), 250)
                && data.independent() != null
                && data.status() != null
                && hasLength(data.sourceAuthority(), 128)
                && hasOptionalLength(data.sourceReference(), 300)
                && hasOptionalLength(data.sourceRevision(), 64)
                && data.createdAt() != null
                && hasLength(data.createdBy(), 128)
                && data.updatedAt() != null
                && hasLength(data.updatedBy(), 128)
                && data.version() != null
                && data.version() >= 0;
    }

    private boolean isValidNotFound(CountryReferenceResponse response) {
        return response != null
                && Integer.valueOf(404).equals(response.status())
                && COUNTRY_NOT_FOUND.equals(response.code())
                && response.data() == null;
    }

    private boolean isTechnicalFailure(Throwable failure) {
        return !(failure instanceof ApplicationException)
                && !containsCancellation(failure);
    }

    private ApplicationException malformedResponse() {
        return dependencyUnavailable(
                new IllegalStateException("Geographic Reference Service returned a malformed response"));
    }

    private ApplicationException dependencyUnavailable(Throwable cause) {
        return new ApplicationException(
                new ApplicationFailure.DependencyUnavailable(DEPENDENCY_NAME),
                cause);
    }

    private static boolean matches(String value, String pattern) {
        return value != null && value.matches(pattern);
    }

    private static boolean hasLength(String value, int maximumLength) {
        return value != null
                && !value.isBlank()
                && value.codePointCount(0, value.length()) <= maximumLength;
    }

    private static boolean hasOptionalLength(String value, int maximumLength) {
        return value == null || value.codePointCount(0, value.length()) <= maximumLength;
    }

    private static boolean hasExactFields(JsonNode object, Set<String> expectedFields) {
        return object != null
                && object.isObject()
                && object.size() == expectedFields.size()
                && expectedFields.stream().allMatch(object::has);
    }

    private static boolean hasValidSuccessTypes(JsonNode payload) {
        JsonNode data = payload.get("data");
        return hasValidErrorTypes(payload)
                && data.get("id").isTextual()
                && matches(data.get("id").textValue(), UUID_PATTERN)
                && data.get("alpha2Code").isTextual()
                && data.get("alpha3Code").isTextual()
                && data.get("numericCode").isTextual()
                && data.get("defaultName").isTextual()
                && data.get("officialName").isTextual()
                && data.get("independent").isBoolean()
                && data.get(STATUS_FIELD).isTextual()
                && isTextualOrNull(data.get("validFrom"))
                && isTextualOrNull(data.get("validUntil"))
                && data.get("sourceAuthority").isTextual()
                && isTextualOrNull(data.get("sourceReference"))
                && isTextualOrNull(data.get("sourceRevision"))
                && data.get("createdAt").isTextual()
                && data.get("createdBy").isTextual()
                && data.get("updatedAt").isTextual()
                && data.get("updatedBy").isTextual()
                && data.get("version").isIntegralNumber();
    }

    private static boolean hasValidErrorTypes(JsonNode payload) {
        return payload.get(STATUS_FIELD).isIntegralNumber()
                && payload.get("code").isTextual();
    }

    private static boolean isTextualOrNull(JsonNode value) {
        return value.isTextual() || value.isNull();
    }

    private static boolean containsCancellation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof CancellationException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }
}
