package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageSlice;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchCriteria;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import io.smallrye.mutiny.Uni;

import java.util.Optional;

/** Reads safe detached global catalog representations and bounded ascending keyset slices. */
public interface IdentifierSchemeReadPort {

    /** Reads current catalog data in any lifecycle state, returning explicit absence. */
    Uni<Optional<IdentifierSchemeResult>> findById(IdentifierSchemeId id);

    /** Matches exact case-sensitive catalog code without normalization or tenant ownership. */
    Uni<Optional<IdentifierSchemeResult>> findByCode(String code);

    /** Returns at most the criteria limit and available neighbors, always in ascending creation-time/UUID order. */
    Uni<IdentifierSchemePageSlice> findPage(IdentifierSchemeSearchCriteria criteria,
            Optional<IdentifierSchemePageBoundary> boundary);
}
