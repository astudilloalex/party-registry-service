package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.ws.rs.core.HttpHeaders;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Centralizes strict request-body, UUID, and operation-header validation.
 */
@ApplicationScoped
public class ApiRequestSupport {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IF_MATCH_HEADER = "If-Match";

    private static final System.Logger LOGGER = System.getLogger(ApiRequestSupport.class.getName());
    private static final String PARTY_ID_FIELD = "partyId";
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
            throw badRequest(PartyResponseCode.REQUEST_BODY_REQUIRED, "body");
        }
        // The public envelope carries one code: required fields precede other constraints, then paths sort alphabetically.
        List<ConstraintViolation<T>> violations = validator.validate(request).stream()
                .sorted(Comparator.comparing((ConstraintViolation<T> violation) ->
                        !violation.getMessageTemplate().endsWith("-required"))
                        .thenComparing(violation -> violation.getPropertyPath().toString())
                        .thenComparing(ConstraintViolation::getMessageTemplate))
                .toList();
        if (!violations.isEmpty()) {
            for (ConstraintViolation<T> violation : violations) {
                PartyResponseCode code = validationCode(violation);
                LOGGER.log(System.Logger.Level.WARNING,
                        "Request rejected status=400 code={0} source=validateBody model={1} field={2} rule={0}",
                        code.getCode(), request.getClass().getSimpleName(), violation.getPropertyPath());
            }
            throw new ApiResponseException(validationCode(violations.getFirst()));
        }
        return request;
    }

    /**
     * Validates a normalized copy while retaining original values for command mapping and idempotency.
     *
     * @param request original request body
     * @param validationCopy function producing a separate validation representation
     * @param <T> request body type
     * @return the unchanged original request after successful validation
     */
    public <T> T validateBody(T request, UnaryOperator<T> validationCopy) {
        if (request == null) {
            return validateBody(request);
        }
        validateBody(validationCopy.apply(request));
        return request;
    }

    /**
     * Parses a canonical UUID path value as a Party identifier.
     */
    public PartyId parsePartyId(String value) {
        if (value == null) {
            throw badRequest(PartyResponseCode.PARTY_ID_INVALID, PARTY_ID_FIELD);
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw badRequest(PartyResponseCode.PARTY_ID_INVALID, PARTY_ID_FIELD);
            }
            return new PartyId(parsed);
        } catch (IllegalArgumentException _) {
            throw badRequest(PartyResponseCode.PARTY_ID_INVALID, PARTY_ID_FIELD);
        }
    }

    /**
     * Reads exactly one nonblank bounded idempotency key.
     */
    public String requireIdempotencyKey(HttpHeaders headers) {
        return readIdempotencyKey(headers, true)
                .orElseThrow(() -> badRequest(PartyResponseCode.IDEMPOTENCY_KEY_REQUIRED, IDEMPOTENCY_KEY_HEADER));
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
        String value = requireVersionHeader(headers);
        if (!NONNEGATIVE_DECIMAL.matcher(value).matches()) {
            throw badRequest(PartyResponseCode.IF_MATCH_INVALID, IF_MATCH_HEADER);
        }
        try {
            return new PartyVersion(Long.parseLong(value));
        } catch (NumberFormatException _) {
            throw badRequest(PartyResponseCode.IF_MATCH_OUT_OF_RANGE, IF_MATCH_HEADER);
        }
    }

    private static Optional<String> readIdempotencyKey(HttpHeaders headers, boolean required) {
        List<String> values = headerValues(headers, IDEMPOTENCY_KEY_HEADER);
        if (values.isEmpty()) {
            if (required) {
                throw badRequest(PartyResponseCode.IDEMPOTENCY_KEY_REQUIRED, IDEMPOTENCY_KEY_HEADER);
            }
            return Optional.empty();
        }
        if (values.size() != 1) {
            throw badRequest(PartyResponseCode.IDEMPOTENCY_KEY_DUPLICATED, IDEMPOTENCY_KEY_HEADER);
        }
        String value = values.getFirst();
        if (value == null || value.isBlank()) {
            throw badRequest(PartyResponseCode.IDEMPOTENCY_KEY_BLANK, IDEMPOTENCY_KEY_HEADER);
        }
        if (value.codePointCount(0, value.length()) > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw badRequest(PartyResponseCode.IDEMPOTENCY_KEY_TOO_LONG, IDEMPOTENCY_KEY_HEADER);
        }
        return Optional.of(value);
    }

    private static String requireVersionHeader(HttpHeaders headers) {
        List<String> values = headerValues(headers, IF_MATCH_HEADER);
        if (values.isEmpty()) {
            throw badRequest(PartyResponseCode.IF_MATCH_REQUIRED, IF_MATCH_HEADER);
        }
        if (values.size() != 1) {
            throw badRequest(PartyResponseCode.IF_MATCH_DUPLICATED, IF_MATCH_HEADER);
        }
        if (values.getFirst() == null || values.getFirst().isBlank()) {
            throw badRequest(PartyResponseCode.IF_MATCH_INVALID, IF_MATCH_HEADER);
        }
        return values.getFirst();
    }

    private static List<String> headerValues(HttpHeaders headers, String headerName) {
        if (headers == null) {
            throw badRequest(PartyResponseCode.BAD_REQUEST, headerName);
        }
        List<String> values = headers.getRequestHeader(headerName);
        return values == null ? List.of() : values;
    }

    private static PartyResponseCode validationCode(ConstraintViolation<?> violation) {
        for (PartyResponseCode code : PartyResponseCode.values()) {
            if (code.getStatus() == 400 && code.getCode().equals(violation.getMessageTemplate())) {
                return code;
            }
        }
        return PartyResponseCode.BAD_REQUEST;
    }

    private static ApiResponseException badRequest(PartyResponseCode code, String field) {
        LOGGER.log(System.Logger.Level.WARNING,
                "Request rejected status=400 code={0} source=request-validation field={1} rule={0}",
                code.getCode(), field);
        return new ApiResponseException(code);
    }
}
