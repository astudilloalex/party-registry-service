package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Objects;

/** Carries exact effective creation values without prematurely evaluating semantic configuration. */
public record IdentifierSchemeCreateInput(
        String code, String issuingCountryCode, IdentifierCategory category,
        IdentifierSubjectType applicableSubjectType, String name, @Nullable String description,
        String normalizerKey, String validatorKey, @Nullable BigInteger minimumLength,
        @Nullable BigInteger maximumLength, boolean requiresExpiration) implements IdentifierSchemeEffectiveRequest {

    public IdentifierSchemeCreateInput {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(issuingCountryCode, "issuingCountryCode");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(applicableSubjectType, "applicableSubjectType");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(normalizerKey, "normalizerKey");
        Objects.requireNonNull(validatorKey, "validatorKey");
    }

    /** Materializes the omitted expiration flag; transport decoding rejects explicitly null flags first. */
    public static IdentifierSchemeCreateInput withDefaults(
            String code, String country, IdentifierCategory category, IdentifierSubjectType subjectType,
            String name, @Nullable String description, String normalizer, String validator,
            @Nullable BigInteger minimum, @Nullable BigInteger maximum, @Nullable Boolean expiration) {
        return new IdentifierSchemeCreateInput(code, country, category, subjectType, name, description,
                normalizer, validator, minimum, maximum, Boolean.TRUE.equals(expiration));
    }

    @Override
    public String operation() {
        return "identifier-scheme.create.v1";
    }
}
