package com.alexastudillo.partyregistry.application.error;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;

import java.io.Serial;
import java.util.Map;
import java.util.Objects;

/**
 * Carries a transport-neutral application failure across reactive boundaries.
 */
public final class ApplicationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final String FAILURE = "failure";

    private static final Map<Class<? extends ApplicationFailure>, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry(ApplicationFailure.IdentifierSchemeNotFound.class, "Identifier scheme not found"),
            Map.entry(ApplicationFailure.IdentifierSchemeCodeConflict.class, "Identifier scheme code conflict"),
            Map.entry(ApplicationFailure.IdentifierSchemeVersionMismatch.class, "Identifier scheme version mismatch"),
            Map.entry(ApplicationFailure.IdentifierSchemeRulesLocked.class, "Identifier scheme processing properties are locked"),
            Map.entry(ApplicationFailure.IdentifierSchemeRetired.class, "Identifier scheme is retired"),
            Map.entry(ApplicationFailure.InvalidIdentifierSchemeLifecycle.class, "Invalid identifier scheme lifecycle transition"),
            Map.entry(ApplicationFailure.IdentifierSchemeVersionExhausted.class, "Identifier scheme version is exhausted"),
            Map.entry(ApplicationFailure.IdentifierSchemeLengthRangeInvalid.class, "Invalid identifier scheme length range"),
            Map.entry(ApplicationFailure.InvalidIdentifierSchemeConfiguration.class, "Invalid identifier scheme configuration"),
            Map.entry(ApplicationFailure.InvalidIdentifierSchemeCursor.class, "Invalid identifier scheme cursor"),
            Map.entry(ApplicationFailure.InvalidPartyCursor.class, "Invalid Party cursor"),
            Map.entry(ApplicationFailure.InvalidNationalityCursor.class, "Invalid nationality cursor"),
            Map.entry(ApplicationFailure.NationalityNotFound.class, "Nationality not found"),
            Map.entry(ApplicationFailure.UnrecognizedNationalityCountry.class, "Unrecognized nationality country"),
            Map.entry(ApplicationFailure.NationalityValidityInvalid.class, "Invalid nationality validity period"),
            Map.entry(ApplicationFailure.NationalityValidityConflict.class, "Nationality validity conflict"),
            Map.entry(ApplicationFailure.PrimaryNationalityConflict.class, "Primary nationality conflict"),
            Map.entry(ApplicationFailure.NationalityNotEffective.class, "Nationality is not effective"),
            Map.entry(ApplicationFailure.NaturalPersonNotFound.class, "Natural person not found"),
            Map.entry(ApplicationFailure.LegalEntityNotFound.class, "Legal entity not found"),
            Map.entry(ApplicationFailure.IdempotencyKeyConflict.class, "Idempotency key conflict"),
            Map.entry(ApplicationFailure.ExpectedVersionMismatch.class, "Expected version mismatch"),
            Map.entry(ApplicationFailure.UnrecognizedBirthCountry.class, "Unrecognized birth country"),
            Map.entry(ApplicationFailure.UnrecognizedIncorporationCountry.class, "Unrecognized incorporation country"),
            Map.entry(ApplicationFailure.UnknownIdentifierScheme.class, "Unknown identifier scheme"),
            Map.entry(ApplicationFailure.InactiveIdentifierScheme.class, "Inactive identifier scheme"),
            Map.entry(ApplicationFailure.IncompatibleIdentifierScheme.class, "Incompatible identifier scheme"),
            Map.entry(ApplicationFailure.IdentifierValidationFailure.class, "Identifier validation failed"),
            Map.entry(ApplicationFailure.IdentifierUniquenessConflict.class, "Identifier uniqueness conflict"),
            Map.entry(ApplicationFailure.PartyNotFound.class, "Party not found"),
            Map.entry(ApplicationFailure.InvalidPartyLifecycle.class, "Invalid Party lifecycle transition"),
            Map.entry(ApplicationFailure.StalePartyVersion.class, "Stale Party version"),
            Map.entry(ApplicationFailure.MissingQualifyingIdentifier.class, "Qualifying Party identifier required"),
            Map.entry(ApplicationFailure.DependencyUnavailable.class, "Dependency unavailable"),
            Map.entry(ApplicationFailure.PersistenceFailure.class, "Persistence operation failed"),
            Map.entry(ApplicationFailure.IdentifierCatalogFailure.class, "Identifier catalog is internally inconsistent"),
            Map.entry(ApplicationFailure.InvalidBusinessState.class, "Invalid business state"));

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
        String description = DESCRIPTIONS.get(failure.getClass());
        if (description != null) {
            return description;
        }
        throw new IllegalArgumentException("Unsupported application failure: " + failure.getClass().getName());
    }
}
