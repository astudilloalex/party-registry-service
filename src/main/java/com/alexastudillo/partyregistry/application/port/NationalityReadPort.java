package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.alexastudillo.partyregistry.application.model.NationalityPageBoundary;
import com.alexastudillo.partyregistry.application.model.NationalityPageSlice;
import com.alexastudillo.partyregistry.application.model.NationalitySearchCriteria;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;

import java.util.Optional;

/** Reads detached tenant/Party-qualified nationality detail and consistent pages. */
public interface NationalityReadPort {

    /** Returns whether the Party belongs to the caller, irrespective of its lifecycle or type. */
    Uni<Boolean> partyExists(TenantId tenantId, PartyId partyId);

    /** Reads a record only under its owning tenant and Party, including historical records. */
    Uni<Optional<NationalityResult>> findById(TenantId tenantId, PartyId partyId, NationalityId nationalityId);

    /** Counts, reads, and locates page neighbors in one consistent snapshot; absence means no owned Party. */
    Uni<Optional<NationalityPageSlice>> findPage(
            TenantId tenantId, PartyId partyId, NationalitySearchCriteria criteria,
            Optional<NationalityPageBoundary> boundary);

}
