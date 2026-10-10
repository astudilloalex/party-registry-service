package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.ExactIdentifierSearchQuery;
import com.alexastudillo.partyregistry.application.model.IdentifierSearchPage;
import com.alexastudillo.partyregistry.application.port.IdentifierSearchReadPort;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelectorPolicy;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelector;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import io.smallrye.mutiny.Uni;

import java.util.Objects;

/** Coordinates same-view scheme resolution, normalizer-only historical equality and complete safe exact-result metadata. */
public final class SearchPartiesByIdentifierUseCase {
    private final IdentifierSearchReadPort reads;
    private final IdentifierSearchSelectorPolicy selectors;

    public SearchPartiesByIdentifierUseCase(IdentifierSearchReadPort reads, IdentifierSearchSelectorPolicy selectors) {
        this.reads = Objects.requireNonNull(reads, "reads");
        this.selectors = Objects.requireNonNull(selectors, "selectors");
    }

    /** Resolves scheme and normalization even for empty filters; historical values never undergo registration admission. */
    public Uni<IdentifierSearchPage> execute(ExactIdentifierSearchQuery query) {
        return reads.withRead(query.tenantId(), context -> context.findSchemeByCode(query.schemeCode()).flatMap(found -> {
            var scheme = found.orElseThrow(() -> new ApplicationException(new ApplicationFailure.UnknownIdentifierScheme(query.schemeCode())));
            var selector = select(scheme, query.value());
            return context.findExact(scheme.id(), selector, query.filters());
        })).map(IdentifierSearchPage::exact);
    }

    private IdentifierSearchSelector select(IdentifierScheme scheme, String value) {
        try {
            return selectors.select(scheme, value);
        } catch (DomainValidationException failure) {
            throw new ApplicationException(new ApplicationFailure.IdentifierValidationFailure(failure.violation()));
        }
    }
}
