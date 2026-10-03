package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemePage;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageSlice;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchScope;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeCursorPort;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeReadPort;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.query.ListIdentifierSchemesQuery;
import io.smallrye.mutiny.Uni;

import java.util.Objects;

/** Authenticates tenant-bound navigation before assembling bounded detached global catalog pages. */
public final class ListIdentifierSchemesUseCase {
    private final IdentifierSchemeReadPort reads;
    private final IdentifierSchemeCursorPort cursors;
    private final OperationObservationPort observation;

    public ListIdentifierSchemesUseCase(IdentifierSchemeReadPort reads, IdentifierSchemeCursorPort cursors) {
        this(reads, cursors, OperationObservation.noop());
    }

    /** Composes bounded navigation with subscription-scoped neutral observation. */
    public ListIdentifierSchemesUseCase(IdentifierSchemeReadPort reads, IdentifierSchemeCursorPort cursors,
            OperationObservationPort observation) {
        this.reads = Objects.requireNonNull(reads, "reads");
        this.cursors = Objects.requireNonNull(cursors, "cursors");
        this.observation = Objects.requireNonNull(observation, "observation");
    }

    /** Preserves exact effective criteria and returns available authenticated continuations without full-catalog totals. */
    public Uni<IdentifierSchemePage> execute(ListIdentifierSchemesQuery query) {
        Objects.requireNonNull(query, "query");
        return OperationObservation.observe(query.requestMetadata(), ObservedOperation.APPLICATION_IDENTIFIER_SCHEME_LIST, observation, () -> {
            var scope = new IdentifierSchemeSearchScope(query.requestMetadata().tenantId(), query.criteria());
            var boundary = query.cursor().map(token -> cursors.decode(token, scope));
            return reads.findPage(scope.criteria(), boundary).map(slice -> toPage(slice, scope));
        }, ignored -> OperationOutcome.RETRIEVED);
    }

    private IdentifierSchemePage toPage(IdentifierSchemePageSlice slice, IdentifierSchemeSearchScope scope) {
        if (slice.items().size() > scope.criteria().limit()) {
            throw new IllegalStateException("Identifier scheme query exceeded the requested page size");
        }
        return new IdentifierSchemePage(slice.items(),
                slice.next().map(position -> cursors.encode(
                        new IdentifierSchemePageBoundary(IdentifierSchemePageBoundary.Direction.NEXT, position), scope)),
                slice.previous().map(position -> cursors.encode(
                        new IdentifierSchemePageBoundary(IdentifierSchemePageBoundary.Direction.PREVIOUS, position), scope)));
    }
}
