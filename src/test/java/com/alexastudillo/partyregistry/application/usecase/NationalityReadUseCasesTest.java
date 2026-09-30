package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.command.GetNationalityQuery;
import com.alexastudillo.partyregistry.application.command.ListNationalitiesQuery;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NationalityPageBoundary;
import com.alexastudillo.partyregistry.application.model.NationalityPagePosition;
import com.alexastudillo.partyregistry.application.model.NationalityPageSlice;
import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.alexastudillo.partyregistry.application.model.NationalitySearchCriteria;
import com.alexastudillo.partyregistry.application.model.NationalitySearchScope;
import com.alexastudillo.partyregistry.application.port.NationalityCursorPort;
import com.alexastudillo.partyregistry.application.port.NationalityReadPort;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies absent-record distinctions, date-scoped cursors, and validation before data access. */
class NationalityReadUseCasesTest {

    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final PartyId PARTY = new PartyId(UUID.randomUUID());
    private static final NationalityId ID = new NationalityId(UUID.randomUUID());
    private static final Instant CREATED = Instant.parse("2026-09-23T10:00:00Z");
    private static final NationalityResult RESULT = new NationalityResult(ID, PARTY, "EC", false,
            null, null, CREATED, CREATED);
    private final StubReads reads = new StubReads();
    private final StubCursors cursors = new StubCursors();

    @Test
    void checksPartyOwnershipBeforeLookingForNationality() {
        GetNationalityUseCase useCase = new GetNationalityUseCase(reads);
        reads.owned = false;
        var query = new GetNationalityQuery(TENANT, PARTY, ID);
        assertInstanceOf(ApplicationFailure.PartyNotFound.class,
                failure(useCase.execute(query)).failure());
        assertEquals(0, reads.detailCalls);
        reads.owned = true;
        reads.detail = Optional.empty();
        assertInstanceOf(ApplicationFailure.NationalityNotFound.class,
                failure(useCase.execute(query)).failure());
        reads.detail = Optional.of(RESULT);
        assertEquals(RESULT, await(useCase.execute(query)));
    }

    @Test
    void authenticatesBeforeReadingAndSignsExactDetachedBoundaries() {
        ListNationalitiesUseCase useCase = new ListNationalitiesUseCase(reads, cursors);
        LocalDate day = LocalDate.of(2026, Month.SEPTEMBER, 23);
        var criteria = new NationalitySearchCriteria("EC", false, day, true, 1);
        NationalityPagePosition position = new NationalityPagePosition(CREATED, ID);
        reads.slice = Optional.of(new NationalityPageSlice(List.of(RESULT), 2,
                Optional.of(position), Optional.empty()));
        var query = new ListNationalitiesQuery(TENANT, PARTY, criteria, Optional.of("signed:2026-09-23"));
        var page = await(useCase.execute(query));
        assertEquals(1, reads.pageCalls);
        assertEquals(criteria, reads.criteria);
        assertEquals("signed:2026-09-23", page.nextCursor().orElseThrow());
        assertEquals(2, page.totalElements());
        assertEquals(List.of(RESULT), page.items());
        assertEquals(NationalityPageBoundary.Direction.NEXT, cursors.lastEncoded.direction());
        assertEquals(position, cursors.lastEncoded.position());
        assertEquals(new NationalitySearchScope(TENANT, PARTY, criteria), cursors.lastScope);

        var nextDay = new NationalitySearchCriteria("EC", false, day.plusDays(1), true, 1);
        var invalidCursorQuery = new ListNationalitiesQuery(TENANT, PARTY, nextDay, query.cursor());
        assertInstanceOf(ApplicationFailure.InvalidNationalityCursor.class,
                failure(useCase.execute(invalidCursorQuery)).failure());
        assertEquals(1, reads.pageCalls);
        reads.slice = Optional.empty();
        var partyNotFoundQuery = new ListNationalitiesQuery(TENANT, PARTY, criteria, Optional.empty());
        assertInstanceOf(ApplicationFailure.PartyNotFound.class,
                failure(useCase.execute(partyNotFoundQuery)).failure());
    }

    private static <T> T await(Uni<T> operation) {
        return operation.await().atMost(Duration.ofSeconds(2));
    }

    private static ApplicationException failure(Uni<?> operation) {
        return assertThrows(ApplicationException.class, () -> await(operation));
    }

    /** Supplies deterministic detached reads without crossing a database boundary. */
    private static final class StubReads implements NationalityReadPort {
        private boolean owned = true;
        private int detailCalls;
        private int pageCalls;
        private Optional<NationalityResult> detail = Optional.of(RESULT);
        private Optional<NationalityPageSlice> slice = Optional.empty();
        private NationalitySearchCriteria criteria;

        @Override
        public Uni<Boolean> partyExists(TenantId tenantId, PartyId partyId) {
            return Uni.createFrom().item(owned);
        }

        @Override
        public Uni<Optional<NationalityResult>> findById(TenantId tenantId, PartyId partyId, NationalityId nationalityId) {
            detailCalls++;
            return Uni.createFrom().item(detail);
        }

        @Override
        public Uni<Optional<NationalityPageSlice>> findPage(TenantId tenantId, PartyId partyId,
                NationalitySearchCriteria effective, Optional<NationalityPageBoundary> boundary) {
            pageCalls++;
            criteria = effective;
            return Uni.createFrom().item(slice);
        }
    }

    /** Rejects tokens from another effective date before the read port is consulted. */
    private static final class StubCursors implements NationalityCursorPort {
        private NationalityPageBoundary lastEncoded;
        private NationalitySearchScope lastScope;

        @Override
        public NationalityPageBoundary decode(String token, NationalitySearchScope expectedScope) {
            if (!token.equals("signed:" + expectedScope.criteria().asOfDate())) {
                throw new ApplicationException(new ApplicationFailure.InvalidNationalityCursor());
            }
            return new NationalityPageBoundary(NationalityPageBoundary.Direction.NEXT,
                    new NationalityPagePosition(CREATED, ID));
        }

        @Override
        public String encode(NationalityPageBoundary boundary, NationalitySearchScope scope) {
            lastEncoded = boundary;
            lastScope = scope;
            return "signed:" + scope.criteria().asOfDate();
        }
    }
}
