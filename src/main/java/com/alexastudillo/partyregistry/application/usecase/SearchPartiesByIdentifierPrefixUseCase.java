package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.*;
import com.alexastudillo.partyregistry.application.port.IdentifierSearchCursorPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSearchReadPort;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelectorPolicy;
import com.alexastudillo.partyregistry.domain.policy.IdentifierSearchSelector;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import io.smallrye.mutiny.Uni;

import java.util.Objects;
import java.util.Optional;

/** Coordinates normalization, canonical cursor scope and safe live prefix pages without owning persistence or cryptography. */
public final class SearchPartiesByIdentifierPrefixUseCase {
    private final IdentifierSearchReadPort reads;
    private final IdentifierSearchCursorPort cursors;
    private final IdentifierSearchSelectorPolicy selectors;
    private final OperationObservationPort observations;

    public SearchPartiesByIdentifierPrefixUseCase(IdentifierSearchReadPort reads, IdentifierSearchCursorPort cursors,
            IdentifierSearchSelectorPolicy selectors) {
        this(reads, cursors, selectors, OperationObservation.noop());
    }

    public SearchPartiesByIdentifierPrefixUseCase(IdentifierSearchReadPort reads, IdentifierSearchCursorPort cursors,
            IdentifierSearchSelectorPolicy selectors, OperationObservationPort observations) {
        this.reads = Objects.requireNonNull(reads, "reads");
        this.cursors = Objects.requireNonNull(cursors, "cursors");
        this.selectors = Objects.requireNonNull(selectors, "selectors");
        this.observations = Objects.requireNonNull(observations, "observations");
    }

    /** Observes each subscription using accepted attribution; selectors and cursors never enter observation contracts. */
    public Uni<IdentifierSearchPage> execute(PrefixIdentifierSearchQuery query, RequestMetadata metadata) {
        if (!query.tenantId().equals(metadata.tenantId())) throw new IllegalArgumentException("Search attribution owner is inconsistent");
        return OperationObservation.observe(metadata, ObservedOperation.APPLICATION_IDENTIFIER_PREFIX_SEARCH,
                observations, () -> execute(query), ignored -> OperationOutcome.RETRIEVED);
    }

    /** Validates scheme/normalization before cursor, including empty filters; signs navigation after the read has closed. */
    public Uni<IdentifierSearchPage> execute(PrefixIdentifierSearchQuery query) {
        return reads.withRead(query.tenantId(), context -> context.findSchemeByCode(query.schemeCode()).flatMap(found -> {
            var scheme = found.orElseThrow(() -> new ApplicationException(new ApplicationFailure.UnknownIdentifierScheme(query.schemeCode())));
            var selector = select(scheme, query.prefix());
            var scope = new IdentifierSearchScope(query.tenantId(), scheme.id(), scheme.code(), selector, query.filters(), query.limit());
            String token = query.cursor();
            Optional<PartyIdentifierPageBoundary> boundary = token == null ? Optional.empty() : Optional.of(cursors.decode(scope, token));
            return context.findPrefix(scope, boundary).map(slice -> new ResolvedSlice(scope, slice));
        })).map(resolved -> {
            var scope = resolved.scope();
            var slice = resolved.slice();
            String next = slice.next().map(position -> cursors.encode(scope,
                    new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.NEXT, position))).orElse(null);
            String previous = slice.previous().map(position -> cursors.encode(scope,
                    new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.PREVIOUS, position))).orElse(null);
            return IdentifierSearchPage.prefix(slice, scope.limit(), next, previous);
        });
    }

    private IdentifierSearchSelector select(IdentifierScheme scheme, String value) {
        try {
            return selectors.select(scheme, value);
        } catch (DomainValidationException failure) {
            throw new ApplicationException(new ApplicationFailure.IdentifierValidationFailure(failure.violation()));
        }
    }

    /** Retains only canonical criteria and safe read results across the closed-session cursor-assembly boundary. */
    private record ResolvedSlice(IdentifierSearchScope scope, PrefixIdentifierSearchSlice slice) {
        @Override public String toString() { return "ResolvedIdentifierSearchSlice[redacted]"; }
    }
}
