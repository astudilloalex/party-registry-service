package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

import java.math.BigInteger;
import java.util.Objects;

/** Carries only editable scheme configuration with explicit omission and null-clearing semantics. */
public record IdentifierSchemeChanges(
        FieldUpdate<String> name,
        FieldUpdate<String> description,
        FieldUpdate<String> normalizerKey,
        FieldUpdate<String> validatorKey,
        FieldUpdate<BigInteger> minimumLength,
        FieldUpdate<BigInteger> maximumLength,
        FieldUpdate<Boolean> requiresExpiration) {

    public IdentifierSchemeChanges {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(normalizerKey, "normalizerKey");
        Objects.requireNonNull(validatorKey, "validatorKey");
        Objects.requireNonNull(minimumLength, "minimumLength");
        Objects.requireNonNull(maximumLength, "maximumLength");
        Objects.requireNonNull(requiresExpiration, "requiresExpiration");
        if (!name.isPresent() && !description.isPresent() && !normalizerKey.isPresent() && !validatorKey.isPresent()
                && !minimumLength.isPresent() && !maximumLength.isPresent() && !requiresExpiration.isPresent()) {
            throw new DomainValidationException(DomainViolation.EMPTY_PATCH,
                    "At least one editable identifier scheme property is required");
        }
    }

    /** Reports submitted processing properties by presence, including identical values and explicit nulls. */
    public boolean hasProcessingChanges() {
        return normalizerKey.isPresent() || validatorKey.isPresent() || minimumLength.isPresent()
                || maximumLength.isPresent() || requiresExpiration.isPresent();
    }

    /** Creates validated prospective configuration while preserving identity, lifecycle, version, and audit. */
    public IdentifierScheme merge(IdentifierScheme current) {
        Objects.requireNonNull(current, "current");
        var bounds = new IdentifierSchemeLengthBounds(current.minimumLength(), current.maximumLength())
                .merge(minimumLength, maximumLength);
        return new IdentifierScheme(current.id(), current.code(), current.issuingCountryCode(), current.category(),
                current.applicableSubjectType(), name.isPresent() ? name.value() : current.name(),
                description.isPresent() ? description.value() : current.description(),
                normalizerKey.isPresent() ? normalizerKey.value() : current.normalizerKey(),
                validatorKey.isPresent() ? validatorKey.value() : current.validatorKey(),
                bounds.minimumLength(), bounds.maximumLength(), expirationMetadata(current), current.status(),
                current.version(), current.auditInfo());
    }

    private boolean expirationMetadata(IdentifierScheme current) {
        if (!requiresExpiration.isPresent()) {
            return current.requiresExpiration();
        }
        Boolean value = requiresExpiration.value();
        if (value == null) {
            throw new DomainValidationException(DomainViolation.IDENTIFIER_SCHEME_EXPIRATION_METADATA_REQUIRED,
                    "Identifier scheme expiration metadata cannot be null");
        }
        return value;
    }
}
