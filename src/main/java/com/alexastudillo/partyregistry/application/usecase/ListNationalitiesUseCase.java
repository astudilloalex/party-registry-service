package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.ListNationalitiesQuery;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NationalityPage;
import com.alexastudillo.partyregistry.application.model.NationalityPageBoundary;
import com.alexastudillo.partyregistry.application.model.NationalityPageSlice;
import com.alexastudillo.partyregistry.application.model.NationalitySearchScope;
import com.alexastudillo.partyregistry.application.port.NationalityCursorPort;
import com.alexastudillo.partyregistry.application.port.NationalityReadPort;
import io.smallrye.mutiny.Uni;

import java.util.Objects;
import java.util.Optional;

/** Authenticates nationality continuations before assembling a tenant-scoped detached page. */
public final class ListNationalitiesUseCase {

    private final NationalityReadPort reads;
    private final NationalityCursorPort cursors;

    public ListNationalitiesUseCase(NationalityReadPort reads, NationalityCursorPort cursors) {
        this.reads = Objects.requireNonNull(reads, "reads");
        this.cursors = Objects.requireNonNull(cursors, "cursors");
    }

    /** Preserves the query's single UTC evaluation date across cursor checks and data selection. */
    public Uni<NationalityPage> execute(ListNationalitiesQuery query) {
        Objects.requireNonNull(query, "query");
        return Uni.createFrom().deferred(() -> {
            NationalitySearchScope scope = new NationalitySearchScope(
                    query.tenantId(), query.partyId(), query.criteria());
            Optional<NationalityPageBoundary> boundary = query.cursor().map(token -> cursors.decode(token, scope));
            return reads.findPage(scope.tenantId(), scope.partyId(), scope.criteria(), boundary)
                    .flatMap(slice -> slice.map(page -> Uni.createFrom().item(toPage(page, scope)))
                            .orElseGet(() -> Uni.createFrom().failure(new ApplicationException(
                                    new ApplicationFailure.PartyNotFound(scope.partyId(), scope.tenantId())))));
        });
    }

    private NationalityPage toPage(NationalityPageSlice slice, NationalitySearchScope scope) {
        if (slice.items().size() > scope.criteria().limit()) {
            throw new IllegalStateException("Nationality query exceeded the requested page size");
        }
        return new NationalityPage(slice.items(), slice.totalElements(), scope.criteria().limit(),
                slice.next().map(position -> cursors.encode(
                        new NationalityPageBoundary(NationalityPageBoundary.Direction.NEXT, position), scope)),
                slice.previous().map(position -> cursors.encode(
                        new NationalityPageBoundary(NationalityPageBoundary.Direction.PREVIOUS, position), scope)));
    }
}
