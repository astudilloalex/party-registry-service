package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.IdempotentCreationResult;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;

import java.util.Optional;

/**
 * Output port for atomic idempotent natural-person creation.
 *
 * <p>
 * Implementations persist the new aggregate together with a versioned
 * application-result snapshot in one atomic unit. Concurrent equivalent
 * requests produce one observable natural person and identical replayed
 * results.
 */
public interface IdempotentNaturalPersonCreationPort {

        /**
         * Resolves an already completed creation before external validation.
         *
         * <p>
         * An equal request hash returns the immutable original result, a
         * different hash fails with an idempotency conflict, and an unused key
         * returns empty.
         *
         * @param tenantId       owning tenant
         * @param idempotencyKey client-supplied idempotency key
         * @param requestHash    canonical fingerprint of the effective create command
         * @return the completed result, or empty when the key is unused
         * @throws com.alexastudillo.partyregistry.application.error.ApplicationException
         *                                                                                with
         *                                                                                {@link com.alexastudillo.partyregistry.application.error.ApplicationFailure.IdempotencyKeyConflict}
         *                                                                                when
         *                                                                                the
         *                                                                                key
         *                                                                                identifies
         *                                                                                a
         *                                                                                different
         *                                                                                effective
         *                                                                                request
         */
        Uni<Optional<IdempotentCreationResult>> findCompleted(
                        TenantId tenantId,
                        String idempotencyKey,
                        String requestHash);

        /**
         * Creates the natural person idempotently for the tenant and key.
         *
         * <p>
         * This operation remains the concurrency arbiter after a preflight miss.
         * When another request wins the key race with an equal hash, the stored
         * original result is replayed. When the winning hash differs, the returned
         * {@link Uni} fails.
         *
         * @param tenantId       owning tenant
         * @param idempotencyKey client-supplied idempotency key
         * @param requestHash    canonical fingerprint of the effective create command
         * @param naturalPerson  aggregate to create
         * @return the created or replayed result
         * @throws com.alexastudillo.partyregistry.application.error.ApplicationException
         *                                                                                with
         *                                                                                {@link com.alexastudillo.partyregistry.application.error.ApplicationFailure.IdempotencyKeyConflict}
         *                                                                                when
         *                                                                                the
         *                                                                                key
         *                                                                                identifies
         *                                                                                a
         *                                                                                different
         *                                                                                effective
         *                                                                                request,
         *                                                                                or
         *                                                                                with
         *                                                                                another
         *                                                                                transport-neutral
         *                                                                                application
         *                                                                                failure
         *                                                                                when
         *                                                                                persistence
         *                                                                                cannot
         *                                                                                complete
         */
        Uni<IdempotentCreationResult> createIdempotently(
                        TenantId tenantId,
                        String idempotencyKey,
                        String requestHash,
                        NaturalPerson naturalPerson);
}
