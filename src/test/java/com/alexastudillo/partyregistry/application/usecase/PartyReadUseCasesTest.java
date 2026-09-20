package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyNamePredicate;
import com.alexastudillo.partyregistry.application.model.PartyPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyPagePosition;
import com.alexastudillo.partyregistry.application.model.PartyPageResult;
import com.alexastudillo.partyregistry.application.model.PartyPageSlice;
import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartySearchScope;
import com.alexastudillo.partyregistry.application.model.PartySummaryResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyCursorPort;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import com.alexastudillo.partyregistry.application.query.GetPartyQuery;
import com.alexastudillo.partyregistry.application.query.ListPartiesQuery;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.RecordingOperationObserver;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Verifies qualified absence, preserved historical detail, cursor-before-read ordering, and exact directional page assembly. */
class PartyReadUseCasesTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atTime(12, 0, 0, 123456000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final PartyId PARTY_ID = new PartyId(UUID.randomUUID());
    private static final RequestMetadata METADATA = new RequestMetadata(TENANT, "reader", UUID.randomUUID());
    private final Queries queries = new Queries();
    private final Cursors cursors = new Cursors();
    private final RecordingOperationObserver observations = new RecordingOperationObserver();

    @Test
    void retrievesBothStoredSubtypesInEveryStateWithoutAdditionalCalls() {
        var useCase = new GetPartyUseCase(queries, observations);
        for (PartyType type : PartyType.values()) {
            for (PartyRecordStatus status : PartyRecordStatus.values()) {
                PartyDetailsResult expected = detail(type, status);
                queries.details = Optional.of(expected);
                assertSame(expected, useCase.execute(new GetPartyQuery(METADATA, PARTY_ID)).await().atMost(TIMEOUT));
                assertEquals(TENANT, queries.tenant);
                assertEquals(PARTY_ID, queries.partyId);
            }
        }
        assertEquals(8, queries.detailReads);
        assertEquals(0, queries.pageReads);
        assertEquals(8, observations.completedCalls().size());
        assertEquals(new RecordingOperationObserver.CompletedCall(METADATA, ObservedOperation.APPLICATION_PARTY_RETRIEVAL,
                OperationOutcome.RETRIEVED), observations.completedCalls().getFirst());
    }

    @Test
    void qualifiedAbsenceHasTheSamePartyNotFoundFailureForAnyTenant() {
        for (TenantId tenant : List.of(TENANT, new TenantId(UUID.randomUUID()))) {
            var metadata = new RequestMetadata(tenant, "reader", UUID.randomUUID());
            var query = new GetPartyQuery(metadata, PARTY_ID);
            Throwable failure = failure(new GetPartyUseCase(queries, observations).execute(query));
            var application = assertInstanceOf(ApplicationException.class, failure);
            assertEquals(new ApplicationFailure.PartyNotFound(PARTY_ID, tenant), application.failure());
            assertEquals(tenant, queries.tenant);
            assertEquals(OperationOutcome.NOT_FOUND, observations.completedCalls().getLast().outcome());
        }
    }

    @Test
    void cursorValidationPrecedesPersistenceAndPreservesItsFailure() {
        cursors.failure = new ApplicationException(new ApplicationFailure.InvalidPartyCursor());
        var query = new ListPartiesQuery(METADATA, criteria(50), "invalid-token");
        Uni<PartyPageResult> operation = new ListPartiesUseCase(queries, cursors, observations).execute(query);
        assertEquals(0, queries.pageReads);
        assertNull(cursors.decodedScope);
        assertEquals(List.of(), observations.startedCalls());
        assertSame(cursors.failure, failure(operation));
        assertEquals(new PartySearchScope(TENANT, query.criteria()), cursors.decodedScope);
        assertEquals(0, queries.pageReads);
        assertEquals(new RecordingOperationObserver.CompletedCall(METADATA, ObservedOperation.APPLICATION_PARTY_LIST,
                OperationOutcome.VALIDATION_FAILED), observations.completedCalls().getFirst());
    }

    @Test
    void passesEffectiveScopeAndExactDecodedBoundaryAndSignsBothDirections() {
        var first = summary(PARTY_ID);
        var last = summary(new PartyId(UUID.randomUUID()));
        queries.page = new PartyPageSlice(List.of(first, last), 5, Optional.of(last.position()), Optional.of(first.position()));
        var decoded = new PartyPageBoundary(PartyPageBoundary.Direction.PREVIOUS, new PartyPagePosition(CLOCK.instant().plusNanos(1), PARTY_ID));
        cursors.decoded = decoded;
        var criteria = new PartySearchCriteria(PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED,
                new PartyNamePredicate("  HISTÓRICAL ", "label"), CLOCK.instant().minusSeconds(1), CLOCK.instant().plusSeconds(1), 2);
        var query = new ListPartiesQuery(METADATA, criteria, "signed-boundary");
        var result = new ListPartiesUseCase(queries, cursors, observations).execute(query).await().atMost(TIMEOUT);
        assertEquals(TENANT, queries.tenant);
        assertEquals(criteria, queries.criteria);
        assertEquals(Optional.of(decoded), queries.boundary);
        assertEquals(List.of(first, last), result.items());
        assertEquals(5, result.totalElements());
        assertEquals(3, result.totalPages());
        assertEquals(2, result.numberOfElements());
        assertEquals("NEXT", result.nextCursor());
        assertEquals("PREVIOUS", result.prevCursor());
        assertEquals(List.of(new PartyPageBoundary(PartyPageBoundary.Direction.NEXT, last.position()),
                new PartyPageBoundary(PartyPageBoundary.Direction.PREVIOUS, first.position())), cursors.encoded);
        assertEquals(List.of(new PartySearchScope(TENANT, criteria), new PartySearchScope(TENANT, criteria)), cursors.scopes);
        assertEquals(1, queries.pageReads);
        assertEquals(0, queries.detailReads);
    }

    @Test
    void emptyFirstPageHasExplicitAbsentDirectionsAndZeroTotals() {
        PartyPageResult page = new ListPartiesUseCase(queries, cursors, observations)
                .execute(new ListPartiesQuery(METADATA, criteria(50), null)).await().atMost(TIMEOUT);
        assertEquals(List.of(), page.items());
        assertEquals(Optional.empty(), queries.boundary);
        assertEquals(0, page.totalElements());
        assertEquals(0, page.totalPages());
        assertEquals(0, page.numberOfElements());
        assertNull(page.nextCursor());
        assertNull(page.prevCursor());
        assertNull(cursors.decodedScope);
        assertEquals(List.of(), cursors.encoded);
    }

    @Test
    void emptyContinuationRetainsNavigationFromTheAuthenticatedBoundary() {
        var position = new PartyPagePosition(CLOCK.instant(), PARTY_ID);
        cursors.decoded = new PartyPageBoundary(PartyPageBoundary.Direction.NEXT, position);
        queries.page = new PartyPageSlice(List.of(), 3, Optional.empty(), Optional.of(position));
        PartyPageResult page = new ListPartiesUseCase(queries, cursors, observations)
                .execute(new ListPartiesQuery(METADATA, criteria(2), "boundary-no-longer-present")).await().atMost(TIMEOUT);
        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(0, page.numberOfElements());
        assertNull(page.nextCursor());
        assertEquals("PREVIOUS", page.prevCursor());
        assertEquals(List.of(new PartyPageBoundary(PartyPageBoundary.Direction.PREVIOUS, position)), cursors.encoded);
    }

    @Test
    void exactPageCalculationDoesNotOverflowAtTheLongBoundary() {
        queries.page = new PartyPageSlice(List.of(), Long.MAX_VALUE, Optional.empty(), Optional.empty());
        PartyPageResult page = new ListPartiesUseCase(queries, cursors, observations)
                .execute(new ListPartiesQuery(METADATA, criteria(200), null)).await().atMost(TIMEOUT);
        assertEquals(Long.MAX_VALUE, page.totalElements());
        assertEquals(46_116_860_184_273_880L, page.totalPages());
    }

    @Test
    void preservesPortFailuresAndCancellationWithoutPartialResults() {
        for (Throwable expected : List.of(new IllegalStateException("Controlled persistence failure"),
                new CancellationException("Controlled cancellation"))) {
            queries.failure = expected;
            assertSame(expected, failure(new GetPartyUseCase(queries, observations).execute(new GetPartyQuery(METADATA, PARTY_ID))));
            assertSame(expected, failure(new ListPartiesUseCase(queries, cursors, observations)
                    .execute(new ListPartiesQuery(METADATA, criteria(50), null))));
            assertEquals(expected instanceof CancellationException ? OperationOutcome.CANCELLED : OperationOutcome.INTERNAL_FAILURE,
                    observations.completedCalls().getLast().outcome());
        }
    }

    private static Throwable failure(Uni<?> operation) {
        var received = new AtomicReference<Throwable>();
        operation.subscribe().withSubscriber(UniAssertSubscriber.create()).awaitFailure(received::set, TIMEOUT).assertFailed();
        return received.get();
    }

    private static PartySearchCriteria criteria(int limit) {
        return new PartySearchCriteria(null, null, new PartyNamePredicate(null, null), null, null, limit);
    }

    private static PartySummaryResult summary(PartyId id) {
        return new PartySummaryResult(id, PartyType.NATURAL_PERSON, "Historical Label", PartyRecordStatus.ARCHIVED, CLOCK.instant(), new PartyVersion(4));
    }

    private static PartyDetailsResult detail(PartyType type, PartyRecordStatus status) {
        var audit = AuditInfo.initial(CLOCK.instant(), "historical-actor");
        Party party = switch (type) {
            case NATURAL_PERSON -> NaturalPerson.restore(PARTY_ID, TENANT, " Historical Label ", status, new PartyVersion(4), audit,
                    new NaturalPersonDetails("Mixed", "Case", null, null, null, "GB"));
            case LEGAL_ENTITY -> LegalEntity.restore(PARTY_ID, TENANT, " Historical Company ", status, new PartyVersion(4), audit,
                    new LegalEntityDetails("Mixed Company", null, null, "GB", null, null));
        };
        return PartyDetailsResult.fromAggregate(party);
    }

    /** Records the only persistence capability available to the read workflows and returns configured safe results. */
    private static final class Queries implements PartyQueryPort {
        private Optional<PartyDetailsResult> details = Optional.empty();
        private PartyPageSlice page = new PartyPageSlice(List.of(), 0, Optional.empty(), Optional.empty());
        private TenantId tenant;
        private PartyId partyId;
        private PartySearchCriteria criteria;
        private Optional<PartyPageBoundary> boundary;
        private Throwable failure;
        private int detailReads;
        private int pageReads;

        @Override
        public Uni<Optional<PartyDetailsResult>> findDetails(TenantId tenantId, PartyId requestedId) {
            tenant = tenantId;
            partyId = requestedId;
            detailReads++;
            return failure == null ? Uni.createFrom().item(details) : Uni.createFrom().failure(failure);
        }

        @Override
        public Uni<PartyPageSlice> findPage(TenantId tenantId, PartySearchCriteria requested, Optional<PartyPageBoundary> position) {
            tenant = tenantId;
            criteria = requested;
            boundary = position;
            pageReads++;
            return failure == null ? Uni.createFrom().item(page) : Uni.createFrom().failure(failure);
        }
    }

    /** Captures authenticated-scope and direction contracts independently of the cryptographic adapter. */
    private static final class Cursors implements PartyCursorPort {
        private PartySearchScope decodedScope;
        private PartyPageBoundary decoded;
        private ApplicationException failure;
        private final List<PartyPageBoundary> encoded = new ArrayList<>();
        private final List<PartySearchScope> scopes = new ArrayList<>();

        @Override
        public PartyPageBoundary decode(String token, PartySearchScope expectedScope) {
            decodedScope = expectedScope;
            if (failure != null) {
                throw failure;
            }
            return decoded;
        }

        @Override
        public String encode(PartyPageBoundary boundary, PartySearchScope scope) {
            encoded.add(boundary);
            scopes.add(scope);
            return boundary.direction().name();
        }
    }
}
