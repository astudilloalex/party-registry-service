package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;

import java.time.LocalDate;
import java.util.List;

/**
 * Reads current tenant-owned Party identifiers as safe masked projections.
 */
public interface PartyIdentifierReadPort {

    /**
     * Finds every PENDING_VERIFICATION or VERIFIED identifier whose expiration is
     * absent or on or after the evaluation date. Includes primary and non-primary
     * identifiers under every scheme lifecycle, including deprecated and retired
     * schemes; scheme metadata is read without applying registration eligibility.
     *
     * @param tenantId owning tenant
     * @param partyId Party whose identifiers are requested
     * @param evaluatedOn date used to evaluate expiration inclusively
     * @return the complete immutable masked list ordered by createdAt ascending,
     *         then identifier ID ascending, or an empty list when none match
     */
    Uni<List<PartyIdentifierResult>> findCurrentByParty(
            TenantId tenantId,
            PartyId partyId,
            LocalDate evaluatedOn);
}
