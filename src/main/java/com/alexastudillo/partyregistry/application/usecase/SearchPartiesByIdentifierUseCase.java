package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.ExactIdentifierSearchQuery;
import com.alexastudillo.partyregistry.application.model.IdentifierSearchPage;
import com.alexastudillo.partyregistry.application.port.IdentifierSearchReadPort;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
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
    private final OperationObservationPort observations;

    public SearchPartiesByIdentifierUseCase(IdentifierSearchReadPort reads, IdentifierSearchSelectorPolicy selectors) {
        this(reads, selectors, OperationObservation.noop());
    }

    public SearchPartiesByIdentifierUseCase(IdentifierSearchReadPort reads, IdentifierSearchSelectorPolicy selectors,
            OperationObservationPort observations) {
        this.reads = Objects.requireNonNull(reads, "reads");
        this.selectors = Objects.requireNonNull(selectors, "selectors");
        this.observations = Objects.requireNonNull(observations, "observations");
    }

    /** Observes each subscription using accepted attribution without binding search criteria to telemetry. */
    public Uni<IdentifierSearchPage> execute(ExactIdentifierSearchQuery query, RequestMetadata metadata) {
        if (!query.tenantId().equals(metadata.tenantId())) throw new IllegalArgumentException("Search attribution owner is inconsistent");
        return OperationObservation.observe(metadata, ObservedOperation.APPLICATION_IDENTIFIER_EXACT_SEARCH,
                observations, () -> execute(query), ignored -> OperationOutcome.RETRIEVED);
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
