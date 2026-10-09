package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPagePosition;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageSlice;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchScope;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierCursorPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierQueryPort;
import com.alexastudillo.partyregistry.application.port.PartyLookupPort;
import com.alexastudillo.partyregistry.application.query.GetPartyIdentifierQuery;
import com.alexastudillo.partyregistry.application.query.ListPartyIdentifiersQuery;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.testing.PartyIdentifierFixtures;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies identifier query ownership, cursor precedence and safe page assembly with isolated inner-port doubles. */
class PartyIdentifierQueryUseCasesTest {

    @Test
    void detailResolvesOwnerFirstAndPropagatesEveryHistoricalStateForBothTypes() {
        for (PartyType type : PartyType.values()) {
            for (PartyIdentifierStatus status : PartyIdentifierStatus.values()) {
                Harness harness = new Harness(type, status);
                var get = new GetPartyIdentifierUseCase(harness.owners(), harness);
                Uni<PartyIdentifierResult> work = get.execute(harness.detailQuery());
                assertTrue(harness.calls.isEmpty());
                PartyIdentifierResult result = item(work);
                assertSame(harness.safe, result);
                assertEquals(List.of("owner", "detail"), harness.calls);
            }
        }
    }

    @Test
    void missingPartyPrecedesIdentifierAccess() {
        Harness harness = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED);
        harness.owner = Optional.empty();
        var get = new GetPartyIdentifierUseCase(harness.owners(), harness);
        ApplicationException failure = assertInstanceOf(ApplicationException.class, failure(get.execute(harness.detailQuery())));
        assertInstanceOf(ApplicationFailure.PartyNotFound.class, failure.failure());
        assertEquals(List.of("owner"), harness.calls);
    }

    @Test
    void missingIdentifierIsDistinctAfterAnOwnedParty() {
        Harness harness = new Harness(PartyType.LEGAL_ENTITY, PartyIdentifierStatus.REVOKED);
        harness.detail = Optional.empty();
        var get = new GetPartyIdentifierUseCase(harness.owners(), harness);
        ApplicationException failure = assertInstanceOf(ApplicationException.class, failure(get.execute(harness.detailQuery())));
        assertInstanceOf(ApplicationFailure.IdentifierNotFound.class, failure.failure());
        assertEquals(List.of("owner", "detail"), harness.calls);
    }

    @Test
    void invalidCursorFailsBeforeAnyOwnerOrIdentifierRead() {
        Harness harness = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED);
        harness.cursorFailure = Optional.of(new ApplicationException(new ApplicationFailure.InvalidIdentifierCursor()));
        var list = new ListPartyIdentifiersUseCase(harness.owners(), harness, harness);
        ApplicationException failure = assertInstanceOf(ApplicationException.class, failure(list.execute(harness.listQuery("opaque-input", 2))));
        assertInstanceOf(ApplicationFailure.InvalidIdentifierCursor.class, failure.failure());
        assertEquals(List.of("decode"), harness.calls);
    }

    @Test
    void authenticatedContinuationDoesNotBypassOwnerAbsence() {
        Harness harness = new Harness(PartyType.LEGAL_ENTITY, PartyIdentifierStatus.VERIFIED);
        harness.owner = Optional.empty();
        var list = new ListPartyIdentifiersUseCase(harness.owners(), harness, harness);
        ApplicationException failure = assertInstanceOf(ApplicationException.class, failure(list.execute(harness.listQuery("opaque-input", 2))));
        assertInstanceOf(ApplicationFailure.PartyNotFound.class, failure.failure());
        assertEquals(List.of("decode", "owner"), harness.calls);
    }

    @Test
    void ownedEmptyPagesSucceedForBothTypesWithoutInventingNavigation() {
        for (PartyType type : PartyType.values()) {
            Harness harness = new Harness(type, PartyIdentifierStatus.EXPIRED);
            var list = new ListPartyIdentifiersUseCase(harness.owners(), harness, harness);
            var page = item(list.execute(harness.listQuery(null, 50)));
            assertTrue(page.items().isEmpty());
            assertEquals(0, page.totalElements());
            assertEquals(0, page.totalPages());
            assertNull(page.nextCursor());
            assertNull(page.prevCursor());
            assertEquals(List.of("owner", "page"), harness.calls);
        }
    }

    @Test
    void preservesSafeRowsFullBoundaryScopeAndOverflowSafeLongTotals() {
        Harness harness = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.REJECTED);
        harness.slice = new PartyIdentifierPageSlice(List.of(harness.safe), Long.MAX_VALUE,
                Optional.of(harness.position()), Optional.of(harness.position()));
        var query = harness.listQuery("opaque-input", 2);
        var list = new ListPartyIdentifiersUseCase(harness.owners(), harness, harness);
        var page = item(list.execute(query));
        assertEquals(List.of(harness.safe), page.items());
        assertEquals(Long.MAX_VALUE, page.totalElements());
        assertEquals(Long.MAX_VALUE / 2 + 1, page.totalPages());
        assertEquals("cursor-NEXT", page.nextCursor());
        assertEquals("cursor-PREVIOUS", page.prevCursor());
        assertEquals(query.scope(), harness.selectedScope);
        assertEquals(new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.NEXT, harness.position()), harness.selectedBoundary);
        assertEquals(List.of("decode", "owner", "page", "encode:NEXT", "encode:PREVIOUS"), harness.calls);
    }

    @Test
    void oversizedPortPagesFailClosedBeforeGeneratingCursors() {
        Harness harness = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED);
        harness.slice = new PartyIdentifierPageSlice(List.of(harness.safe, harness.safe), 2, Optional.empty(), Optional.empty());
        var list = new ListPartyIdentifiersUseCase(harness.owners(), harness, harness);
        assertInstanceOf(IllegalStateException.class, failure(list.execute(harness.listQuery(null, 1))));
        assertEquals(List.of("owner", "page"), harness.calls);
    }

    @Test
    void preservesClassifiedAndUnexpectedPortFailures() {
        for (RuntimeException problem : List.of(new ApplicationException(new ApplicationFailure.DependencyUnavailable("party-store")),
                new IllegalStateException("synthetic-internal-failure"))) {
            Harness harness = new Harness(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED);
            harness.readFailure = Optional.of(problem);
            var get = new GetPartyIdentifierUseCase(harness.owners(), harness);
            assertSame(problem, failure(get.execute(harness.detailQuery())));
            harness.calls.clear();
            var list = new ListPartyIdentifiersUseCase(harness.owners(), harness, harness);
            assertSame(problem, failure(list.execute(harness.listQuery(null, 50))));
            assertEquals(List.of("owner", "page"), harness.calls);
        }
    }

    private static <T> T item(Uni<T> work) {
        return work.subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2)).assertCompleted().getItem();
    }

    private static Throwable failure(Uni<?> work) {
        return work.subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure(Duration.ofSeconds(2)).getFailure();
    }

    /** Tracks query capability calls and supplies detached results without persistence or cryptographic dependencies. */
    private static final class Harness implements PartyIdentifierQueryPort, PartyIdentifierCursorPort {
        private final List<String> calls = new ArrayList<>();
        private final PartyIdentifierResult safe;
        private final RequestMetadata metadata = new RequestMetadata(PartyIdentifierFixtures.TENANT, "reader", UUID.randomUUID());
        private Optional<PartyType> owner;
        private Optional<PartyIdentifierResult> detail;
        private PartyIdentifierPageSlice slice = new PartyIdentifierPageSlice(List.of(), 0, Optional.empty(), Optional.empty());
        private Optional<RuntimeException> readFailure = Optional.empty();
        private Optional<RuntimeException> cursorFailure = Optional.empty();
        private @Nullable PartyIdentifierSearchScope selectedScope;
        private @Nullable PartyIdentifierPageBoundary selectedBoundary;

        private Harness(PartyType type, PartyIdentifierStatus status) {
            safe = PartyIdentifierFixtures.result(PartyIdentifierFixtures.identifier(type, status, false, 9,
                    PartyIdentifierFixtures.EVALUATED_ON.minusDays(1)));
            owner = Optional.of(type);
            detail = Optional.of(safe);
        }

        private PartyLookupPort owners() {
            return (tenant, party) -> {
                calls.add("owner");
                assertEquals(metadata.tenantId(), tenant);
                assertEquals(safe.partyId(), party);
                return Uni.createFrom().item(owner);
            };
        }

        @Override
        public Uni<Optional<PartyIdentifierResult>> findById(RequestMetadata request, PartyId party, PartyIdentifierId identifier) {
            calls.add("detail");
            assertEquals(metadata, request);
            assertEquals(safe.identifierId(), identifier);
            return readFailure.map(problem -> Uni.createFrom().<Optional<PartyIdentifierResult>>failure(problem))
                    .orElseGet(() -> Uni.createFrom().item(detail));
        }

        @Override
        public Uni<PartyIdentifierPageSlice> findPage(RequestMetadata request, PartyIdentifierSearchScope scope,
                @Nullable PartyIdentifierPageBoundary boundary) {
            calls.add("page");
            assertEquals(metadata, request);
            selectedScope = scope;
            selectedBoundary = boundary;
            return readFailure.map(problem -> Uni.createFrom().<PartyIdentifierPageSlice>failure(problem))
                    .orElseGet(() -> Uni.createFrom().item(slice));
        }

        @Override
        public String encode(PartyIdentifierSearchScope scope, PartyIdentifierPageBoundary boundary) {
            calls.add("encode:" + boundary.direction());
            assertEquals(selectedScope, scope);
            assertEquals(position(), boundary.position());
            return "cursor-" + boundary.direction();
        }

        @Override
        public PartyIdentifierPageBoundary decode(PartyIdentifierSearchScope scope, String token) {
            calls.add("decode");
            if (cursorFailure.isPresent()) {
                throw cursorFailure.orElseThrow();
            }
            return new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.NEXT, position());
        }

        private GetPartyIdentifierQuery detailQuery() {
            return new GetPartyIdentifierQuery(metadata, safe.partyId(), safe.identifierId());
        }

        private ListPartyIdentifiersQuery listQuery(@Nullable String cursor, int limit) {
            return new ListPartyIdentifiersQuery(metadata, safe.partyId(),
                    new PartyIdentifierSearchCriteria(null, null, null, null, limit), cursor);
        }

        private PartyIdentifierPagePosition position() {
            return new PartyIdentifierPagePosition(safe.createdAt(), safe.identifierId());
        }
    }
}
