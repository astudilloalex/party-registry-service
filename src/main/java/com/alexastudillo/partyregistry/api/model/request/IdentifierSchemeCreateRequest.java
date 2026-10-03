package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/** Carries exact strictly bound creation values before required-field validation. */
@JsonDeserialize(using = IdentifierSchemeCreateRequestDeserializer.class)
public record IdentifierSchemeCreateRequest(
        @Nullable String code, @Nullable String issuingCountryCode, @Nullable IdentifierCategory category,
        @Nullable IdentifierSubjectType applicableSubjectType, @Nullable String name, @Nullable String description,
        @Nullable String normalizerKey, @Nullable String validatorKey, @Nullable BigInteger minimumLength,
        @Nullable BigInteger maximumLength, boolean requiresExpiration) {
}
