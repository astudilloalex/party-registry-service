package com.alexastudillo.partyregistry.domain;

import java.util.Objects;

public final class IdentifierSchemePolicy {
    private IdentifierSchemePolicy() {}

    public static void validateConfiguration(Integer minimumLength, Integer maximumLength) {
        if (minimumLength != null && minimumLength <= 0) {
            throw new DomainViolation("Identifier minimum length must be positive");
        }
        if (maximumLength != null && maximumLength <= 0) {
            throw new DomainViolation("Identifier maximum length must be positive");
        }
        if (minimumLength != null && maximumLength != null && maximumLength < minimumLength) {
            throw new DomainViolation("Identifier maximum length must not be less than its minimum length");
        }
    }

    public static void validateIdentifier(
            PartyType partyType,
            IdentifierSubjectType subjectType,
            Integer minimumLength,
            Integer maximumLength,
            String normalizedIdentifier,
            boolean normalizerAvailable,
            boolean validatorAvailable) {
        Objects.requireNonNull(partyType, "partyType");
        Objects.requireNonNull(subjectType, "subjectType");
        validateConfiguration(minimumLength, maximumLength);
        if (!normalizerAvailable || !validatorAvailable) {
            throw new DomainViolation("The Identifier Scheme requires an unavailable rule implementation");
        }
        if (subjectType != IdentifierSubjectType.BOTH && !subjectType.name().equals(partyType.name())) {
            throw new DomainViolation("The Identifier Scheme is incompatible with the Party type");
        }
        if (normalizedIdentifier == null) {
            throw new DomainViolation("Normalized identifier must not be null");
        }
        int length = normalizedIdentifier.length();
        if (minimumLength != null && length < minimumLength) {
            throw new DomainViolation("Normalized identifier is shorter than the configured minimum");
        }
        if (maximumLength != null && length > maximumLength) {
            throw new DomainViolation("Normalized identifier is longer than the configured maximum");
        }
    }
}
