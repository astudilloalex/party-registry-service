package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.application.command.ListNationalitiesQuery;
import com.alexastudillo.partyregistry.application.model.NationalitySearchCriteria;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Parses exactly the six declared nationality list parameters without silent defaults for invalid input. */
public final class NationalityQueryParameters {

    private static final Set<String> SUPPORTED = Set.of(
            "countryCode", "isPrimary", "asOfDate", "includeExpired", "cursor", "limit");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern DECIMAL = Pattern.compile("\\d+");

    private NationalityQueryParameters() {
    }

    /** Captures the UTC date once when absent and validates every supplied decoded parameter. */
    public static ListNationalitiesQuery parse(
            TenantId tenantId, PartyId partyId, Map<String, List<String>> parameters, Clock clock) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(clock, "clock");
        parameters.forEach((name, values) -> {
            if (name == null || !SUPPORTED.contains(name) || values == null || values.size() != 1
                    || values.getFirst() == null || values.getFirst().isBlank()) {
                throw badRequest();
            }
        });
        try {
            String countryCode = value(parameters, "countryCode");
            if (countryCode != null && !countryCode.matches("[A-Z]{2}")) {
                throw badRequest();
            }
            LocalDate asOfDate = date(value(parameters, "asOfDate"), clock);
            NationalitySearchCriteria criteria = new NationalitySearchCriteria(
                    countryCode, bool(value(parameters, "isPrimary")), asOfDate,
                    Boolean.TRUE.equals(bool(value(parameters, "includeExpired"))),
                    limit(value(parameters, "limit")));
            return new ListNationalitiesQuery(tenantId, partyId, criteria,
                    Optional.ofNullable(value(parameters, "cursor")));
        } catch (IllegalArgumentException | DateTimeException _) {
            throw badRequest();
        }
    }

    private static @Nullable String value(Map<String, List<String>> parameters, String key) {
        List<String> values = parameters.get(key);
        return values == null ? null : values.getFirst();
    }

    private static @Nullable Boolean bool(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw badRequest();
        };
    }

    private static LocalDate date(@Nullable String value, Clock clock) {
        if (value == null) {
            return clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        if (!ISO_DATE.matcher(value).matches()) {
            throw badRequest();
        }
        return LocalDate.parse(value);
    }

    private static int limit(@Nullable String value) {
        if (value == null) {
            return NationalitySearchCriteria.DEFAULT_LIMIT;
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
