package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.application.model.PartyNamePredicate;
import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.query.ListPartiesQuery;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import org.jspecify.annotations.Nullable;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Parses the closed multivalue listing query without silently defaulting malformed input or retaining rejected values in failures. */
public final class PartyQueryParameters {

    private static final Set<String> SUPPORTED = Set.of("type", "recordStatus", "displayNameStartsWith", "displayNameContains",
            "createdFrom", "createdTo", "cursor", "limit");
    private static final Pattern DECIMAL = Pattern.compile("\\d+");

    private PartyQueryParameters() {
    }

    /** Validates decoded parameter cardinality/values and binds the effective criteria to the already accepted request context. */
    public static ListPartiesQuery parse(RequestMetadata metadata, Map<String, List<String>> parameters) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(parameters, "parameters");
        parameters.forEach((name, values) -> {
            if (name == null || !SUPPORTED.contains(name) || values == null || values.size() != 1 || values.getFirst() == null) {
                throw badRequest();
            }
        });
        try {
            String cursor = value(parameters, "cursor");
            if (cursor != null && cursor.isBlank()) {
                throw badRequest();
            }
            var criteria = new PartySearchCriteria(enumeration(PartyType.class, value(parameters, "type")),
                    enumeration(PartyRecordStatus.class, value(parameters, "recordStatus")),
                    new PartyNamePredicate(name(value(parameters, "displayNameStartsWith")), name(value(parameters, "displayNameContains"))),
                    instant(value(parameters, "createdFrom")), instant(value(parameters, "createdTo")), limit(value(parameters, "limit")));
            return new ListPartiesQuery(metadata, criteria, cursor);
        } catch (IllegalArgumentException | DateTimeException _) {
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

    private static @Nullable Instant instant(@Nullable String value) {
        return value == null ? null : OffsetDateTime.parse(value).toInstant();
    }

    private static @Nullable String name(@Nullable String value) {
        if (value != null && value.codePointCount(0, value.length()) > 300) {
            throw badRequest();
        }
        return value;
    }

    private static int limit(@Nullable String value) {
        if (value == null) {
            return PartySearchCriteria.DEFAULT_LIMIT;
        }
        if (!DECIMAL.matcher(value).matches()) {
            throw badRequest();
        }
        return Integer.parseInt(value);
    }

    private static ApiResponseException badRequest() {
        return new ApiResponseException(PartyResponseCode.BAD_REQUEST);
    }
}
