package com.alexastudillo.partyregistry.domain.error;

import java.util.Objects;

/**
 * Reports a domain invariant violation without depending on transport or persistence concerns.
 */
public final class DomainValidationException extends RuntimeException {

    private final DomainViolation violation;

    public DomainValidationException(DomainViolation violation, String message) {
        super(message);
        this.violation = Objects.requireNonNull(violation, "violation");
    }

    public DomainViolation violation() {
        return violation;
    }
}
