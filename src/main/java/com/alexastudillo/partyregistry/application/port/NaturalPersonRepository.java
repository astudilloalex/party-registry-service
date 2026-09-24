package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;

import java.util.Optional;

/**
 * Output port for tenant-scoped natural-person persistence.
 *
 * <p>
 * Implementations retrieve and update natural-person aggregates without
 * exposing persistence entities, sessions, or database exceptions. Lookups
 * never expose legal entities or parties owned by other tenants.
 */
public interface NaturalPersonRepository {

    /**
     * Finds one natural person owned by the tenant.
     *
     * <p>
     * Absent, cross-tenant, and wrong-type parties are all reported as an
     * empty result.
     *
     * @param tenantId owning tenant
     * @param partyId  party identifier
     * @return the matching natural person, or empty when concealed or absent
     */
    Uni<Optional<NaturalPerson>> findByTenantAndId(TenantId tenantId, PartyId partyId);

    /**
     * Persists an updated natural-person aggregate guarded by the expected
     * aggregate version.
     *
     * <p>
     * The version is incremented exactly once when the update succeeds.
     *
     * @param updatedPerson   updated aggregate to persist
     * @param expectedVersion version the caller expects to be current
     * @return the persisted aggregate with its incremented version
     * @throws com.alexastudillo.partyregistry.application.error.ApplicationException
     *                                                                                with
     *                                                                                {@link com.alexastudillo.partyregistry.application.error.ApplicationFailure.ExpectedVersionMismatch}
     *                                                                                when
     *                                                                                the
     *                                                                                current
     *                                                                                version
     *                                                                                differs
     *                                                                                from
     *                                                                                the
     *                                                                                expected
     *                                                                                version,
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
    Uni<NaturalPerson> update(NaturalPerson updatedPerson, PartyVersion expectedVersion);
}
