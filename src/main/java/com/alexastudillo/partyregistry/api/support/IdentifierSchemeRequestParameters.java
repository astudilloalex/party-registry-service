package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSchemeCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSchemePatchRequest;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeCreateInput;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeChanges;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

/** Validates scheme transport fields without admitting rule configuration or narrowing bounds. */
@ApplicationScoped
public class IdentifierSchemeRequestParameters {
    /** Validates the complete body before operation headers and maps exact effective creation input. */
    public IdentifierSchemeCreateInput create(@Nullable IdentifierSchemeCreateRequest request) {
        if (request == null) {
            throw failure(PartyResponseCode.REQUEST_BODY_REQUIRED);
        }
        String code = code(request.code());
        String country = request.issuingCountryCode();
        if (country == null || !country.matches("[A-Z]{2}") || request.category() == null
                || request.applicableSubjectType() == null) {
            throw failure(PartyResponseCode.BAD_REQUEST);
        }
        return new IdentifierSchemeCreateInput(code, country, request.category(), request.applicableSubjectType(),
                requiredText(request.name(), 150), optionalText(request.description(), 500),
                requiredText(request.normalizerKey(), 64), requiredText(request.validatorKey(), 64),
                request.minimumLength(), request.maximumLength(), request.requiresExpiration());
    }

    /** Retains an exact nonblank code and uses the contract's code-specific input failures. */
    public String code(@Nullable String value) {
        if (value == null || value.isBlank()) {
            throw failure(PartyResponseCode.IDENTIFIER_SCHEME_CODE_REQUIRED);
        }
        if (value.codePointCount(0, value.length()) > 64) {
            throw failure(PartyResponseCode.IDENTIFIER_SCHEME_CODE_TOO_LONG);
        }
        return value;
    }

    /** Validates presence and text before path/headers; semantic range, merging, and state remain in Domain. */
    public IdentifierSchemeChanges patch(@Nullable IdentifierSchemePatchRequest request) {
        if (request == null) {
            throw failure(PartyResponseCode.REQUEST_BODY_REQUIRED);
        }
        if (request.empty()) {
            throw failure(PartyResponseCode.PATCH_PROPERTY_REQUIRED);
        }
        if (request.name().isPresent()) {
            requiredText(request.name().value(), 150);
        }
        if (request.description().isPresent()) {
            optionalText(request.description().value(), 500);
        }
        if (request.normalizerKey().isPresent()) {
            requiredText(request.normalizerKey().value(), 64);
        }
        if (request.validatorKey().isPresent()) {
            requiredText(request.validatorKey().value(), 64);
        }
        if (request.requiresExpiration().isPresent() && request.requiresExpiration().value() == null) {
            throw failure(PartyResponseCode.BAD_REQUEST);
        }
        return new IdentifierSchemeChanges(request.name(), request.description(), request.normalizerKey(),
                request.validatorKey(), request.minimumLength(), request.maximumLength(), request.requiresExpiration());
    }

    private static String requiredText(@Nullable String value, int maximum) {
        if (value == null || value.isBlank()) {
            throw failure(PartyResponseCode.BAD_REQUEST);
        }
        optionalText(value, maximum);
        return value;
    }

    private static @Nullable String optionalText(@Nullable String value, int maximum) {
        if (value != null && value.codePointCount(0, value.length()) > maximum) {
            throw failure(PartyResponseCode.BAD_REQUEST);
        }
        return value;
    }

    private static ApiResponseException failure(PartyResponseCode code) {
        return new ApiResponseException(code);
    }
}
