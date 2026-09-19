package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.api.response.contract.ApiResponseCode;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Translates known transport-neutral application failures at the HTTP boundary.
 */
@ApplicationScoped
public final class PartyApiErrorTranslator {

    /**
     * Converts a known application failure and preserves every unknown failure for
     * the global mapper.
     *
     * @param failure failure emitted by a resource pipeline
     * @return an API response exception for known failures, otherwise the original
     *         failure
     */
    public Throwable translate(Throwable failure) {
        if (!(failure instanceof ApplicationException applicationException)) {
            return failure;
        }

        ApiResponseCode responseCode = responseCode(applicationException.failure());
        return responseCode == null
                ? failure
                : new ApiResponseException(responseCode, applicationException);
    }

    private static ApiResponseCode responseCode(ApplicationFailure failure) {
        return switch (failure) {
            case ApplicationFailure.NaturalPersonNotFound _ -> PartyResponseCode.NATURAL_PERSON_NOT_FOUND;
            case ApplicationFailure.LegalEntityNotFound _ -> PartyResponseCode.LEGAL_ENTITY_NOT_FOUND;
            case ApplicationFailure.IdempotencyKeyConflict _ -> PartyResponseCode.IDEMPOTENCY_KEY_CONFLICT;
            case ApplicationFailure.ExpectedVersionMismatch _ -> PartyResponseCode.EXPECTED_VERSION_MISMATCH;
            case ApplicationFailure.UnrecognizedBirthCountry _ -> PartyResponseCode.UNRECOGNIZED_BIRTH_COUNTRY;
            case ApplicationFailure.UnrecognizedIncorporationCountry _ ->
                PartyResponseCode.UNRECOGNIZED_INCORPORATION_COUNTRY;
            case ApplicationFailure.UnknownIdentifierScheme _ -> PartyResponseCode.UNKNOWN_IDENTIFIER_SCHEME;
            case ApplicationFailure.InactiveIdentifierScheme _ -> PartyResponseCode.INACTIVE_IDENTIFIER_SCHEME;
            case ApplicationFailure.IncompatibleIdentifierScheme _ -> PartyResponseCode.INCOMPATIBLE_IDENTIFIER_SCHEME;
            case ApplicationFailure.IdentifierValidationFailure _ -> PartyResponseCode.IDENTIFIER_VALIDATION_FAILURE;
            case ApplicationFailure.MissingQualifyingIdentifier _ -> PartyResponseCode.MISSING_QUALIFYING_IDENTIFIER;
            case ApplicationFailure.IdentifierUniquenessConflict _ -> PartyResponseCode.IDENTIFIER_UNIQUENESS_CONFLICT;
            case ApplicationFailure.InvalidPartyLifecycle _ -> PartyResponseCode.INVALID_PARTY_LIFECYCLE;
            case ApplicationFailure.PartyNotFound _ -> PartyResponseCode.PARTY_NOT_FOUND;
            case ApplicationFailure.StalePartyVersion _ -> PartyResponseCode.STALE_PARTY_VERSION;
            case ApplicationFailure.DependencyUnavailable _ -> PartyResponseCode.DEPENDENCY_UNAVAILABLE;
            case ApplicationFailure.InvalidBusinessState invalid -> businessViolationCode(invalid.violation());
            case ApplicationFailure.PersistenceFailure _,ApplicationFailure.IdentifierCatalogFailure _ -> null;
        };
    }

    private static ApiResponseCode businessViolationCode(DomainViolation violation) {
        return switch (violation) {
            case DISPLAY_NAME_REQUIRED -> PartyResponseCode.BLANK_DISPLAY_NAME;
            case BIRTH_DATE_IN_FUTURE -> PartyResponseCode.BIRTH_DATE_IN_FUTURE;
            case DATE_OF_DEATH_IN_FUTURE -> PartyResponseCode.DATE_OF_DEATH_IN_FUTURE;
            case DEATH_BEFORE_BIRTH -> PartyResponseCode.DEATH_BEFORE_BIRTH;
            case INCORPORATION_DATE_IN_FUTURE -> PartyResponseCode.INCORPORATION_DATE_IN_FUTURE;
            case DISSOLUTION_DATE_IN_FUTURE -> PartyResponseCode.DISSOLUTION_DATE_IN_FUTURE;
            case DISSOLUTION_BEFORE_INCORPORATION -> PartyResponseCode.DISSOLUTION_BEFORE_INCORPORATION;
            // Unclassified invariants can indicate corrupted state or programming errors, not client input.
            default -> null;
        };
    }
}
