package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageSlice;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchScope;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierCursorPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierQueryPort;
import com.alexastudillo.partyregistry.application.port.PartyLookupPort;
import com.alexastudillo.partyregistry.application.query.ListPartyIdentifiersQuery;
import io.smallrye.mutiny.Uni;

import java.util.Objects;

/** Authenticates historical list scope and assembles owned safe pages with exact long totals and opaque navigation. */
public final class ListPartyIdentifiersUseCase {

    private final PartyLookupPort owners;
    private final PartyIdentifierQueryPort identifiers;
    private final PartyIdentifierCursorPort cursors;
    private final OperationObservationPort observation;

    public ListPartyIdentifiersUseCase(PartyLookupPort owners, PartyIdentifierQueryPort identifiers,
            PartyIdentifierCursorPort cursors) {
        this(owners, identifiers, cursors, OperationObservation.noop());
    }

    /** Composes scoped navigation and bounded terminal observation without retaining token or filter payloads. */
    public ListPartyIdentifiersUseCase(PartyLookupPort owners, PartyIdentifierQueryPort identifiers,
            PartyIdentifierCursorPort cursors, OperationObservationPort observation) {
        this.owners = Objects.requireNonNull(owners, "owners");
        this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
        this.cursors = Objects.requireNonNull(cursors, "cursors");
        this.observation = Objects.requireNonNull(observation, "observation");
    }

    /** Returns an owned historical page; malformed continuations and missing owners emit neutral failures. */
    public Uni<PartyIdentifierPageResult> execute(ListPartyIdentifiersQuery query) {
        Objects.requireNonNull(query, "query");
        return OperationObservation.observe(query.requestMetadata(), ObservedOperation.APPLICATION_PARTY_IDENTIFIER_LIST,
                observation, () -> read(query), _ -> OperationOutcome.RETRIEVED);
    }

    private Uni<PartyIdentifierPageResult> read(ListPartyIdentifiersQuery query) {
        return Uni.createFrom().deferred(() -> {
            PartyIdentifierSearchScope scope = query.scope();
            String token = query.cursor();
            PartyIdentifierPageBoundary boundary = token == null ? null : cursors.decode(scope, token);
            return owners.findType(scope.tenantId(), scope.partyId()).flatMap(owner -> {
                if (owner.isEmpty()) {
                    return Uni.createFrom().failure(new ApplicationException(new ApplicationFailure.PartyNotFound(scope.partyId(), scope.tenantId())));
                }
                return identifiers.findPage(query.requestMetadata(), scope, boundary).map(slice -> page(scope, slice));
            });
        });
    }

    private PartyIdentifierPageResult page(PartyIdentifierSearchScope scope, PartyIdentifierPageSlice slice) {
        int limit = scope.criteria().limit();
        if (slice.items().size() > limit) {
            throw new IllegalStateException("Identifier query exceeded the requested page size");
        }
        long total = slice.totalElements();
        long totalPages = total / limit + (total % limit == 0 ? 0 : 1);
        String next = slice.next().map(position -> cursors.encode(scope,
                new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.NEXT, position))).orElse(null);
        String previous = slice.previous().map(position -> cursors.encode(scope,
                new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.PREVIOUS, position))).orElse(null);
        return new PartyIdentifierPageResult(slice.items(), next, previous, total, totalPages);
    }
}
