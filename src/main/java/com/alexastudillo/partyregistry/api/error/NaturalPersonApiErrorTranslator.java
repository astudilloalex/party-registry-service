package com.alexastudillo.partyregistry.api.error;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.api.response.contract.ApiResponseCode;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Translates known transport-neutral application failures at the HTTP boundary.
 */
@ApplicationScoped
public final class NaturalPersonApiErrorTranslator {

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
            case ApplicationFailure.NaturalPersonNotFound _ -> NaturalPersonResponseCode.NOT_FOUND;
            case ApplicationFailure.IdempotencyKeyConflict _ -> NaturalPersonResponseCode.IDEMPOTENCY_CONFLICT;
            case ApplicationFailure.ExpectedVersionMismatch _ -> NaturalPersonResponseCode.PRECONDITION_FAILED;
            case ApplicationFailure.UnrecognizedBirthCountry _ -> NaturalPersonResponseCode.UNPROCESSABLE_ENTITY;
            case ApplicationFailure.DependencyUnavailable _ -> NaturalPersonResponseCode.DEPENDENCY_UNAVAILABLE;
            case ApplicationFailure.InvalidBusinessState _ -> NaturalPersonResponseCode.UNPROCESSABLE_ENTITY;
            case ApplicationFailure.PersistenceFailure _ -> null;
        };
    }
}
