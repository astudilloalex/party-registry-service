package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.model.PartyPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyPageResult;
import com.alexastudillo.partyregistry.application.model.PartyPageSlice;
import com.alexastudillo.partyregistry.application.model.PartySearchScope;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyCursorPort;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import com.alexastudillo.partyregistry.application.query.ListPartiesQuery;
import io.smallrye.mutiny.Uni;

import java.util.Objects;
import java.util.Optional;

/** Validates scoped continuations before reads and assembles exact totals and authenticated navigation from one consistent slice. */
public final class ListPartiesUseCase {

    private final PartyQueryPort queries;
    private final PartyCursorPort cursors;
    private final OperationObservationPort observations;

    public ListPartiesUseCase(PartyQueryPort queries, PartyCursorPort cursors, OperationObservationPort observations) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.cursors = Objects.requireNonNull(cursors, "cursors");
        this.observations = Objects.requireNonNull(observations, "observations");
    }

    /** Performs no persistence access when cursor validation fails and preserves long-valued exact counts. */
    public Uni<PartyPageResult> execute(ListPartiesQuery query) {
        Objects.requireNonNull(query, "query");
        return OperationObservation.observe(query.metadata(), ObservedOperation.APPLICATION_PARTY_LIST, observations, () -> {
            var scope = new PartySearchScope(query.metadata().tenantId(), query.criteria());
            Optional<PartyPageBoundary> boundary = Optional.ofNullable(query.cursor()).map(token -> cursors.decode(token, scope));
            return queries.findPage(scope.tenantId(), scope.criteria(), boundary).map(slice -> toPage(slice, scope));
        }, ignored -> OperationOutcome.RETRIEVED);
    }

    private PartyPageResult toPage(PartyPageSlice slice, PartySearchScope scope) {
        if (slice.items().size() > scope.criteria().limit()) {
            throw new IllegalStateException("Party query exceeded the requested page size");
        }
        String next = slice.next().map(position -> cursors.encode(new PartyPageBoundary(PartyPageBoundary.Direction.NEXT, position), scope))
                .orElse(null);
        String previous = slice.previous().map(position -> cursors.encode(new PartyPageBoundary(PartyPageBoundary.Direction.PREVIOUS, position), scope))
                .orElse(null);
        return new PartyPageResult(slice.items(), next, previous, slice.totalElements(), Math.ceilDiv(slice.totalElements(), scope.criteria().limit()));
    }
}
