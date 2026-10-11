package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.model.ExactIdentifierSearchSlice;
import com.alexastudillo.partyregistry.application.model.IdentifierSearchFilters;
import com.alexastudillo.partyregistry.application.model.IdentifierSearchScope;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageBoundary;
import com.alexastudillo.partyregistry.application.model.PrefixIdentifierSearchSlice;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelector;
import io.smallrye.mutiny.Uni;

import java.util.Optional;

/** Resolves scheme and safe matches sequentially in the owning operation's same consistent read view. */
public interface IdentifierSearchReadContext {
    Uni<Optional<IdentifierScheme>> findSchemeByCode(String exactCode);

    /** Returns every identifier equality match with no decryption, deduplication or page cap. */
    Uni<ExactIdentifierSearchSlice> findExact(IdentifierSchemeId schemeId, IdentifierSearchSelector selector,
            IdentifierSearchFilters filters);

    /** Counts all effective matches before selecting the bounded page relative to an optional live boundary. */
    Uni<PrefixIdentifierSearchSlice> findPrefix(IdentifierSearchScope scope, Optional<PartyIdentifierPageBoundary> boundary);
}
