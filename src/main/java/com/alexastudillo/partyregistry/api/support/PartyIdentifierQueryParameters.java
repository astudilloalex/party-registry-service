package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchCriteria;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.query.ListPartyIdentifiersQuery;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Parses the closed identifier query multimap without normalizing exact filters or authenticating cursors. */
public final class PartyIdentifierQueryParameters {

    private static final Set<String> SUPPORTED = Set.of(
            "identifierSchemeId", "schemeCode", "status", "isPrimary", "cursor", "limit");

    private PartyIdentifierQueryParameters() { }

    /** Validates every supplied decoded parameter and defaults capacity only when absent. */
    public static ListPartyIdentifiersQuery parse(RequestMetadata metadata, PartyId party,
            Map<String, List<String>> parameters) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(parameters, "parameters");
        parameters.forEach((name, values) -> {
            if (name == null || !SUPPORTED.contains(name) || values == null || values.size() != 1
                    || values.getFirst() == null || values.getFirst().isBlank()) {
                throw badRequest();
            }
        });
        try {
            var criteria = new PartyIdentifierSearchCriteria(schemeId(value(parameters, "identifierSchemeId")),
                    value(parameters, "schemeCode"), status(value(parameters, "status")),
                    primary(value(parameters, "isPrimary")), limit(value(parameters, "limit")));
            return new ListPartyIdentifiersQuery(metadata, party, criteria, value(parameters, "cursor"));
        } catch (IllegalArgumentException _) {
            throw badRequest();
        }
    }

    private static @Nullable String value(Map<String, List<String>> parameters, String name) {
        List<String> values = parameters.get(name);
        return values == null ? null : values.getFirst();
    }

    private static @Nullable IdentifierSchemeId schemeId(@Nullable String value) {
        if (value == null) {
            return null;
        }
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw badRequest();
        }
        return new IdentifierSchemeId(parsed);
    }

    private static @Nullable PartyIdentifierStatus status(@Nullable String value) {
        return value == null ? null : PartyIdentifierStatus.valueOf(value);
    }

    private static @Nullable Boolean primary(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw badRequest();
        };
    }

    private static int limit(@Nullable String value) {
        if (value == null) {
            return PartyIdentifierSearchCriteria.DEFAULT_LIMIT;
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
