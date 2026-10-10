package com.alexastudillo.partyregistry.application.error;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;

/** Translates declared identifier Domain decisions to neutral workflow failures while preserving unknown invariant failures. */
public final class PartyIdentifierFailures {
    private PartyIdentifierFailures() { }

    /** Maps only declared state/capacity/validity/primary decisions, leaving unexpected invariants technical. */
    public static RuntimeException translate(DomainValidationException failure) {
        return switch (failure.violation()) {
            case PARTY_IDENTIFIER_INVALID_LIFECYCLE -> new ApplicationException(new ApplicationFailure.InvalidIdentifierLifecycle(), failure);
            case PARTY_IDENTIFIER_VERSION_OVERFLOW -> new ApplicationException(new ApplicationFailure.IdentifierVersionExhausted(), failure);
            case PRIMARY_IDENTIFIER_CONFLICT -> new ApplicationException(new ApplicationFailure.PrimaryIdentifierConflict(), failure);
            case IDENTIFIER_EXPIRED, IDENTIFIER_VALIDITY_DATE_ORDER, IDENTIFIER_ISSUER_CODE_INVALID, IDENTIFIER_ISSUER_CODE_TOO_LONG ->
                    new ApplicationException(new ApplicationFailure.IdentifierValidationFailure(failure.violation()), failure);
            default -> new IllegalStateException("Identifier mutation invariant failed", failure);
        };
    }
}
