package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.api.response.contract.CommonResponseCode;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.ws.rs.core.HttpHeaders;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Centralizes strict request-body, UUID, and operation-header validation.
 */
@ApplicationScoped
public class ApiRequestSupport {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IF_MATCH_HEADER = "If-Match";

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final Pattern NONNEGATIVE_DECIMAL = Pattern.compile("0|[1-9]\\d*");

    private final Validator validator;

    @Inject
    public ApiRequestSupport(Validator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /**
     * Validates a deserialized request body and rejects null bodies uniformly.
     *
     * @param request request body to validate
     * @param <T>     request body type
     * @return the validated request
     */
    public <T> T validateBody(T request) {
        if (request == null) {
            throw badRequest();
        }
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw badRequest();
        }
        return request;
    }

    /**
     * Parses a canonical UUID path value as a Party identifier.
     */
    public PartyId parsePartyId(String value) {
        if (value == null) {
            throw badRequest();
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw badRequest();
            }
            return new PartyId(parsed);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    /**
     * Reads exactly one nonblank bounded idempotency key.
     */
    public String requireIdempotencyKey(HttpHeaders headers) {
        return readIdempotencyKey(headers, true).orElseThrow(ApiRequestSupport::badRequest);
    }

    /**
     * Reads an optional idempotency key while rejecting duplicates and invalid
     * present values.
     */
    public Optional<String> optionalIdempotencyKey(HttpHeaders headers) {
        return readIdempotencyKey(headers, false);
    }

    /**
     * Reads exactly one canonical nonnegative decimal expected version.
     */
    public PartyVersion requireExpectedVersion(HttpHeaders headers) {
        String value = requireSingleHeader(headers, IF_MATCH_HEADER);
        if (!NONNEGATIVE_DECIMAL.matcher(value).matches()) {
            throw badRequest();
        }
        try {
            return new PartyVersion(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw badRequest(exception);
        }
    }

    private static Optional<String> readIdempotencyKey(HttpHeaders headers, boolean required) {
        List<String> values = headerValues(headers, IDEMPOTENCY_KEY_HEADER);
        if (values.isEmpty()) {
            if (required) {
                throw badRequest();
            }
            return Optional.empty();
        }
        if (values.size() != 1) {
            throw badRequest();
        }
        String value = values.getFirst();
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw badRequest();
        }
        return Optional.of(value);
    }

    private static String requireSingleHeader(HttpHeaders headers, String headerName) {
        List<String> values = headerValues(headers, headerName);
        if (values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()) {
            throw badRequest();
        }
        return values.getFirst();
    }

    private static List<String> headerValues(HttpHeaders headers, String headerName) {
        if (headers == null) {
            throw badRequest();
        }
        List<String> values = headers.getRequestHeader(headerName);
        return values == null ? List.of() : values;
    }

    private static ApiResponseException badRequest() {
        return new ApiResponseException(CommonResponseCode.BAD_REQUEST);
    }

    private static ApiResponseException badRequest(Throwable cause) {
        return new ApiResponseException(CommonResponseCode.BAD_REQUEST, cause);
    }
}
