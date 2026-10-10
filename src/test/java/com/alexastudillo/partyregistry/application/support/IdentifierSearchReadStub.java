package com.alexastudillo.partyregistry.application.support;

import com.alexastudillo.partyregistry.application.model.ExactIdentifierSearchSlice;
import com.alexastudillo.partyregistry.application.model.IdentifierSearchFilters;
import com.alexastudillo.partyregistry.application.model.IdentifierSearchScope;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageBoundary;
import com.alexastudillo.partyregistry.application.model.PrefixIdentifierSearchSlice;
import com.alexastudillo.partyregistry.application.port.IdentifierSearchReadContext;
import com.alexastudillo.partyregistry.application.port.IdentifierSearchReadPort;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelector;
import io.smallrye.mutiny.Uni;

import java.util.Optional;
import java.util.function.Function;

/** Supplies deterministic same-callback safe search results for Application orchestration tests. */
public final class IdentifierSearchReadStub implements IdentifierSearchReadPort {
    private final Optional<IdentifierScheme> scheme;
    private final ExactIdentifierSearchSlice exact;
    private final PrefixIdentifierSearchSlice prefix;

    public IdentifierSearchReadStub(Optional<IdentifierScheme> scheme, ExactIdentifierSearchSlice exact,
            PrefixIdentifierSearchSlice prefix) {
        this.scheme = scheme;
        this.exact = exact;
        this.prefix = prefix;
    }

    @Override
    public <T> Uni<T> withRead(TenantId tenantId, Function<IdentifierSearchReadContext, Uni<T>> work) {
        return Uni.createFrom().deferred(() -> work.apply(new ReadContext()));
    }

    /** Keeps scheme and slices from the same configured fixture within each callback's logical read. */
    private final class ReadContext implements IdentifierSearchReadContext {
        @Override
        public Uni<Optional<IdentifierScheme>> findSchemeByCode(String exactCode) {
            return Uni.createFrom().item(scheme.filter(value -> value.code().equals(exactCode)));
        }

        @Override
        public Uni<ExactIdentifierSearchSlice> findExact(IdentifierSchemeId schemeId, IdentifierSearchSelector selector,
                IdentifierSearchFilters filters) {
            return Uni.createFrom().item(exact);
        }

        @Override
        public Uni<PrefixIdentifierSearchSlice> findPrefix(IdentifierSearchScope scope, Optional<PartyIdentifierPageBoundary> boundary) {
            return Uni.createFrom().item(prefix);
        }
    }
}
