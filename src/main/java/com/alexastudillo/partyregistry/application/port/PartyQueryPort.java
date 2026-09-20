package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyPageSlice;
import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;

import java.util.Optional;

/**
 * Reads tenant-qualified root representations without coupling workflows to persistence mechanisms.
 */
public interface PartyQueryPort {

    /** Returns the matching safe subtype detail, or absence for missing and cross-tenant IDs. */
    Uni<Optional<PartyDetailsResult>> findDetails(TenantId tenantId, PartyId partyId);

    /**
     * Reads an exact bounded page in descending tuple order, including consistent totals and neighbors.
     *
     * @param tenantId trusted owner of all eligible records and totals
     * @param criteria effective AND-combined filters and page size
     * @param boundary verified continuation, or empty for the first page
     * @return one internally consistent read, without retaining a snapshot between requests
     */
    Uni<PartyPageSlice> findPage(
            TenantId tenantId, PartySearchCriteria criteria, Optional<PartyPageBoundary> boundary);
}
