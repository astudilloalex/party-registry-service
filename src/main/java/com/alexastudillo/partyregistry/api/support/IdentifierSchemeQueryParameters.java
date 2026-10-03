package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchCriteria;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.query.ListIdentifierSchemesQuery;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Parses the closed scheme query multimap, leaving cursor authentication to the Application port. */
public final class IdentifierSchemeQueryParameters {
    private static final Set<String> SUPPORTED = Set.of(
            "issuingCountryCode", "category", "applicableSubjectType", "status", "cursor", "limit");
    private static final int MAXIMUM_CURSOR_LENGTH = 256;

    private IdentifierSchemeQueryParameters() {
    }

    /** Defaults only absent parameters and retains exact conjunctive filters and the current request scope. */
    public static ListIdentifierSchemesQuery parse(RequestMetadata metadata, Map<String, List<String>> parameters) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(parameters, "parameters");
        parameters.forEach((name, values) -> {
            if (name == null || !SUPPORTED.contains(name) || values == null || values.size() != 1
                    || values.getFirst() == null || values.getFirst().isBlank()) {
                throw badRequest();
            }
        });
        try {
            String cursor = value(parameters, "cursor");
            if (cursor != null && cursor.length() > MAXIMUM_CURSOR_LENGTH) {
                throw badRequest();
            }
            var criteria = new IdentifierSchemeSearchCriteria(value(parameters, "issuingCountryCode"),
                    enumeration(IdentifierCategory.class, value(parameters, "category")),
                    enumeration(IdentifierSubjectType.class, value(parameters, "applicableSubjectType")),
                    enumeration(IdentifierSchemeStatus.class, value(parameters, "status")),
                    limit(value(parameters, "limit")));
            return new ListIdentifierSchemesQuery(metadata, criteria, Optional.ofNullable(cursor));
        } catch (IllegalArgumentException _) {
            throw badRequest();
        }
    }

    private static @Nullable String value(Map<String, List<String>> parameters, String key) {
        List<String> values = parameters.get(key);
        return values == null ? null : values.getFirst();
    }

    private static <E extends Enum<E>> @Nullable E enumeration(Class<E> type, @Nullable String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static int limit(@Nullable String value) {
        if (value == null) {
            return IdentifierSchemeSearchCriteria.DEFAULT_LIMIT;
        }
        if (!value.matches("\\d+")) {
            throw badRequest();
        }
        return Integer.parseInt(value);
    }

    private static ApiResponseException badRequest() {
        return new ApiResponseException(PartyResponseCode.BAD_REQUEST);
    }
}
