package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.math.BigInteger;
import java.util.Objects;

/** Tracks the seven mutable transport properties independently of their nullable values. */
@JsonDeserialize(using = IdentifierSchemePatchRequestDeserializer.class)
public record IdentifierSchemePatchRequest(
        FieldUpdate<String> name, FieldUpdate<String> description,
        FieldUpdate<String> normalizerKey, FieldUpdate<String> validatorKey,
        FieldUpdate<BigInteger> minimumLength, FieldUpdate<BigInteger> maximumLength,
        FieldUpdate<Boolean> requiresExpiration) {
    public IdentifierSchemePatchRequest {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(normalizerKey, "normalizerKey");
        Objects.requireNonNull(validatorKey, "validatorKey");
        Objects.requireNonNull(minimumLength, "minimumLength");
        Objects.requireNonNull(maximumLength, "maximumLength");
        Objects.requireNonNull(requiresExpiration, "requiresExpiration");
    }

    public boolean empty() {
        return !name.isPresent() && !description.isPresent() && !normalizerKey.isPresent()
                && !validatorKey.isPresent() && !minimumLength.isPresent()
                && !maximumLength.isPresent() && !requiresExpiration.isPresent();
    }
}
