package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;

/**
 * Starts framework-neutral observation using only trusted context and bounded semantics.
 */
public interface OperationObservationPort {

    /**
     * Starts one subscription-scoped operation observation.
     *
     * @param requestMetadata validated request context
     * @param operation bounded operation identity
     * @return a terminal observation handle
     */
    StartedObservation start(RequestMetadata requestMetadata, ObservedOperation operation);

    /**
     * Completes an operation observation with one bounded terminal outcome.
     */
    @FunctionalInterface
    interface StartedObservation {

        /**
         * Records the terminal outcome without receiving operation data or failures.
         *
         * @param outcome bounded terminal outcome
         */
        void complete(OperationOutcome outcome);
    }
}
