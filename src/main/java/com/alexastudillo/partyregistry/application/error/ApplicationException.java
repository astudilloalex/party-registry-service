package com.alexastudillo.partyregistry.application.error;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;

import java.util.Objects;

/**
 * Carries a transport-neutral application failure across reactive boundaries.
 */
public final class ApplicationException extends RuntimeException {

    private static final String FAILURE = "failure";

    private final transient ApplicationFailure applicationFailure;

    public ApplicationException(ApplicationFailure failure) {
        super(describe(failure));
        this.applicationFailure = Objects.requireNonNull(failure, FAILURE);
    }

    public ApplicationException(ApplicationFailure failure, Throwable cause) {
        super(describe(failure), cause);
        this.applicationFailure = Objects.requireNonNull(failure, FAILURE);
    }

    /**
     * Wraps a domain invariant violation as an invalid business state failure.
     *
     * @param exception domain validation failure
     * @return the equivalent application exception
     */
    public static ApplicationException of(DomainValidationException exception) {
        Objects.requireNonNull(exception, "exception");
        return new ApplicationException(
                new ApplicationFailure.InvalidBusinessState(exception.violation()),
                exception);
    }

    public ApplicationFailure failure() {
        return applicationFailure;
    }

    private static String describe(ApplicationFailure failure) {
        Objects.requireNonNull(failure, FAILURE);
        return switch (failure) {
            case ApplicationFailure.NaturalPersonNotFound _ -> "Natural person not found";
            case ApplicationFailure.IdempotencyKeyConflict _ -> "Idempotency key conflict";
            case ApplicationFailure.ExpectedVersionMismatch _ -> "Expected version mismatch";
            case ApplicationFailure.UnrecognizedBirthCountry _ -> "Unrecognized birth country";
            case ApplicationFailure.DependencyUnavailable _ -> "Dependency unavailable";
            case ApplicationFailure.PersistenceFailure _ -> "Persistence operation failed";
            case ApplicationFailure.InvalidBusinessState _ -> "Invalid business state";
        };
    }
}
