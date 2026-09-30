package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import io.smallrye.mutiny.Uni;

/**
 * Output port that confirms country references against the Geographic
 * Reference Service.
 */
public interface CountryReferencePort {

    /**
     * Checks whether an uppercase alpha-2 code is a recognized country
     * reference.
     *
     * @param requestMetadata validated context forwarded to the dependency
     * @param alpha2Code      uppercase two-letter country code
     * @return {@code true} when the reference is recognized, {@code false}
     *         when it is not recognized
     * @throws com.alexastudillo.partyregistry.application.error.ApplicationException
     *                                                                                with
     *                                                                                {@link com.alexastudillo.partyregistry.application.error.ApplicationFailure.DependencyUnavailable}
     *                                                                                when
     *                                                                                validation
     *                                                                                cannot
     *                                                                                be
     *                                                                                completed
     *                                                                                because
     *                                                                                the
     *                                                                                Geographic
     *                                                                                Reference
     *                                                                                Service
     *                                                                                is
     *                                                                                unavailable
     */
    Uni<Boolean> isRecognizedCountry(RequestMetadata requestMetadata, String alpha2Code);
}
