package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import org.jspecify.annotations.Nullable;

/** Carries exact conjunctive catalog filters and an effective bounded page size. */
public record IdentifierSchemeSearchCriteria(
        @Nullable String issuingCountryCode, @Nullable IdentifierCategory category,
        @Nullable IdentifierSubjectType applicableSubjectType, @Nullable IdentifierSchemeStatus status, int limit) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAXIMUM_LIMIT = 200;

    public IdentifierSchemeSearchCriteria {
        if (issuingCountryCode != null && !issuingCountryCode.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("Identifier scheme country filter must be uppercase ASCII alpha-2");
        }
        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException("Identifier scheme page size is outside the supported range");
        }
    }
}
