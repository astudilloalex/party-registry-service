package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;

import java.util.Optional;

/**
 * Resolves the immutable type of a tenant-owned Party without exposing details.
 */
public interface PartyLookupPort {

    /**
     * Finds a Party type while concealing absent and cross-tenant records alike.
     *
     * @param tenantId owning tenant
     * @param partyId Party identifier
     * @return the immutable Party type, or empty when concealed or absent
     */
    Uni<Optional<PartyType>> findType(TenantId tenantId, PartyId partyId);
}
