package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.LegalEntityRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.NaturalPersonRegistrationCandidate;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;

import java.util.Optional;

/**
 * Coordinates atomic, idempotent persistence of Party and initial identifier aggregates.
 */
public interface IdempotentPartyRegistrationPort {

    /**
     * Resolves a completed current-operation registration by its keyed fingerprint.
     *
     * @param tenantId owning tenant
     * @param operation current registration operation key
     * @param idempotencyKey client-supplied key
     * @param registrationFingerprint keyed effective-request fingerprint
     * @return the original safe result, or empty when the key is unused
     */
    Uni<Optional<PartyRegistrationResult>> findCompleted(
            TenantId tenantId,
            String operation,
            String idempotencyKey,
            String registrationFingerprint);

    /**
     * Checks a completed operation key without decoding its result snapshot.
     *
     * <p>This supports rejection of legacy natural-person keys whose old inputs can
     * never be equivalent to identifier-required registration.</p>
     *
     * @param tenantId owning tenant
     * @param operation legacy operation key
     * @param idempotencyKey client-supplied key
     * @return {@code true} when the operation key already has a completed outcome
     */
    Uni<Boolean> hasCompletedKey(TenantId tenantId, String operation, String idempotencyKey);

    /**
     * Atomically registers a natural person, initial identifier, replay result, and events.
     *
     * @param candidate complete natural-person registration candidate
     * @return the created or concurrently replayed safe result
     */
    Uni<PartyRegistrationResult> registerNaturalPerson(NaturalPersonRegistrationCandidate candidate);

    /**
     * Atomically registers a legal entity, initial identifier, replay result, and events.
     *
     * @param candidate complete legal-entity registration candidate
     * @return the created or concurrently replayed safe result
     */
    Uni<PartyRegistrationResult> registerLegalEntity(LegalEntityRegistrationCandidate candidate);
}
