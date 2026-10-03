package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePagePosition;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePageSlice;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeSearchCriteria;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeReadPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary.Direction.NEXT;
import static com.alexastudillo.partyregistry.application.model.IdentifierSchemePageBoundary.Direction.PREVIOUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises exact global filters and bounded bidirectional PostgreSQL keysets without catalog counts. */
@QuarkusTest
@Timeout(60)
class IdentifierSchemePageReaderTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T14:00:00.123456Z");

    @Inject
    IdentifierSchemeReadPort reader;
    @Inject
    Mutiny.SessionFactory sessions;
    @Inject
    IdentifierSchemePersistenceMapper mapper;

    @Test
    @RunOnVertxContext
    void traversesTimestampTiesWithUnsignedUuidOrderingAndTrimsBeforeBackwardReversal(UniAsserter asserter) {
        var rows = List.of(
                scheme(UUID.randomUUID(), CREATED.minusNanos(1000)),
                scheme(new UUID(Long.MAX_VALUE, 0), CREATED),
                scheme(new UUID(Long.MAX_VALUE, Long.MAX_VALUE), CREATED),
                scheme(new UUID(Long.MAX_VALUE, Long.MIN_VALUE), CREATED),
                scheme(new UUID(Long.MIN_VALUE, 0), CREATED),
                scheme(new UUID(-1, 0), CREATED),
                scheme(UUID.randomUUID(), CREATED.plusNanos(1000)));
        asserter.execute(() -> persist(rows));
        for (int limit : new int[]{1, 2, 3}) {
            var criteria = criteria(limit);
            for (int start = 0; start < rows.size(); start += limit) {
                int end = Math.min(start + limit, rows.size());
                var expected = rows.subList(start, end);
                var boundary = start == 0 ? Optional.<IdentifierSchemePageBoundary>empty() : boundary(NEXT, rows.get(start - 1));
                boolean hasPrevious = start > 0;
                boolean hasNext = end < rows.size();
                asserter.assertThat(() -> read(criteria, boundary), page -> assertPage(page, expected, hasNext, hasPrevious));
            }
            // Start at the short final page's first row to reproduce every full prior page.
            for (int end = ((rows.size() - 1) / limit) * limit; end > 0; end -= limit) {
                int start = Math.max(0, end - limit);
                var expected = rows.subList(start, end);
                var boundary = boundary(PREVIOUS, rows.get(end));
                boolean hasPrevious = start > 0;
                asserter.assertThat(() -> read(criteria, boundary), page -> assertPage(page, expected, true, hasPrevious));
            }
        }
        asserter.surroundWith(work -> work.eventually(() -> delete(rows)));
    }

    @Test
    @RunOnVertxContext
    void intersectsAllFourFiltersExactlyAndDoesNotWidenNaturalPersonToBoth(UniAsserter asserter) {
        var match = scheme("ZZ", IdentifierCategory.OTHER, IdentifierSubjectType.NATURAL_PERSON, IdentifierSchemeStatus.ACTIVE);
        var rows = List.of(match,
                scheme("ZY", IdentifierCategory.OTHER, IdentifierSubjectType.NATURAL_PERSON, IdentifierSchemeStatus.ACTIVE),
                scheme("ZZ", IdentifierCategory.PASSPORT, IdentifierSubjectType.NATURAL_PERSON, IdentifierSchemeStatus.ACTIVE),
                scheme("ZZ", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, IdentifierSchemeStatus.ACTIVE),
                scheme("ZZ", IdentifierCategory.OTHER, IdentifierSubjectType.LEGAL_ENTITY, IdentifierSchemeStatus.ACTIVE),
                scheme("ZZ", IdentifierCategory.OTHER, IdentifierSubjectType.NATURAL_PERSON, IdentifierSchemeStatus.DRAFT),
                scheme("ZZ", IdentifierCategory.OTHER, IdentifierSubjectType.NATURAL_PERSON, IdentifierSchemeStatus.DEPRECATED),
                scheme("ZZ", IdentifierCategory.OTHER, IdentifierSubjectType.NATURAL_PERSON, IdentifierSchemeStatus.RETIRED));
        asserter.execute(() -> persist(rows));
        var exact = new IdentifierSchemeSearchCriteria("ZZ", IdentifierCategory.OTHER,
                IdentifierSubjectType.NATURAL_PERSON, IdentifierSchemeStatus.ACTIVE, 1);
        asserter.assertThat(() -> read(exact, Optional.empty()), page -> assertPage(page, List.of(match), false, false));
        var both = new IdentifierSchemeSearchCriteria("ZZ", IdentifierCategory.OTHER,
                IdentifierSubjectType.BOTH, IdentifierSchemeStatus.ACTIVE, 200);
        asserter.assertThat(() -> read(both, Optional.empty()), page -> assertPage(page, List.of(rows.get(3)), false, false));
        var statusOmitted = new IdentifierSchemeSearchCriteria("ZZ", IdentifierCategory.OTHER,
                IdentifierSubjectType.NATURAL_PERSON, null, 200);
        asserter.assertThat(() -> read(statusOmitted, Optional.empty()), page -> {
            assertEquals(4, page.items().size());
            assertEquals(java.util.Set.of(IdentifierSchemeStatus.values()),
                    page.items().stream().map(IdentifierSchemeResult::status).collect(java.util.stream.Collectors.toSet()));
        });
        asserter.assertThat(() -> read(new IdentifierSchemeSearchCriteria(null, null, null, null, 200), Optional.empty()), page -> {
            assertTrue(page.items().containsAll(rows.stream().map(IdentifierSchemeResult::fromAggregate).toList()));
            assertTrue(page.items().size() <= 200);
        });
        asserter.surroundWith(work -> work.eventually(() -> delete(rows)));
    }

    @Test
    @RunOnVertxContext
    void omitsBothNeighborsOnEmptyPagesAndProbesRealOppositeBoundaries(UniAsserter asserter) {
        var first = scheme(UUID.randomUUID(), CREATED);
        var last = scheme(UUID.randomUUID(), CREATED.plusNanos(1000));
        var rows = List.of(first, last);
        asserter.execute(() -> persist(rows));
        var criteria = criteria(2);
        asserter.assertThat(() -> read(criteria, Optional.empty()), page -> assertPage(page, rows, false, false));
        asserter.assertThat(() -> read(criteria, boundary(NEXT, last)), page -> assertPage(page, List.of(), false, false));
        asserter.assertThat(() -> read(criteria, boundary(PREVIOUS, first)), page -> assertPage(page, List.of(), false, false));
        var noMatches = new IdentifierSchemeSearchCriteria("ZX", IdentifierCategory.PASSPORT, null, null, 50);
        asserter.assertThat(() -> read(noMatches, Optional.empty()), page -> assertPage(page, List.of(), false, false));
        // Arbitrary positions need actual probes rather than assuming any supplied cursor has an opposite page.
        var before = new IdentifierSchemePageBoundary(NEXT, new IdentifierSchemePagePosition(CREATED.minusSeconds(1), first.id()));
        var after = new IdentifierSchemePageBoundary(PREVIOUS, new IdentifierSchemePagePosition(CREATED.plusSeconds(1), last.id()));
        asserter.assertThat(() -> read(criteria, Optional.of(before)), page -> assertPage(page, rows, false, false));
        asserter.assertThat(() -> read(criteria, Optional.of(after)), page -> assertPage(page, rows, false, false));
        // A removed boundary is still a valid exclusive position and must not invent a previous neighbor.
        asserter.execute(() -> delete(List.of(first)));
        asserter.assertThat(() -> read(criteria, boundary(NEXT, first)), page -> assertPage(page, List.of(last), false, false));
        asserter.surroundWith(work -> work.eventually(() -> delete(rows)));
    }

    @Test
    @RunOnVertxContext
    void boundsDefaultMaximumAndSingleItemPagesWithOneExtraRow(UniAsserter asserter) {
        List<IdentifierScheme> rows = new ArrayList<>();
        for (int i = 0; i < 205; i++) {
            rows.add(scheme(UUID.randomUUID(), CREATED.plusNanos(i * 1000L)));
        }
        asserter.execute(() -> persist(rows));
        for (int limit : new int[]{1, IdentifierSchemeSearchCriteria.DEFAULT_LIMIT, IdentifierSchemeSearchCriteria.MAXIMUM_LIMIT}) {
            asserter.assertThat(() -> read(criteria(limit), Optional.empty()),
                    page -> assertPage(page, rows.subList(0, limit), true, false));
        }
        asserter.assertThat(() -> read(criteria(200), boundary(NEXT, rows.get(199))),
                page -> assertPage(page, rows.subList(200, 205), false, true));
        asserter.assertThat(() -> read(criteria(200), boundary(PREVIOUS, rows.get(200))),
                page -> assertPage(page, rows.subList(0, 200), true, false));
        asserter.surroundWith(work -> work.eventually(() -> delete(rows)));
    }

    private Uni<IdentifierSchemePageSlice> read(IdentifierSchemeSearchCriteria criteria,
            Optional<IdentifierSchemePageBoundary> boundary) {
        return timed(reader.findPage(criteria, boundary));
    }

    private Uni<Void> persist(List<IdentifierScheme> rows) {
        return timed(sessions.withTransaction((session, transaction) -> {
            Uni<Void> sequence = Uni.createFrom().voidItem();
            for (IdentifierScheme row : rows) {
                sequence = sequence.call(() -> session.persist(mapper.toEntity(row)));
            }
            return sequence;
        }));
    }

    private Uni<Void> delete(List<IdentifierScheme> rows) {
        return timed(sessions.withTransaction((session, transaction) -> session.createMutationQuery(
                "delete from IdentifierSchemeEntity where id in :ids")
                .setParameter("ids", rows.stream().map(row -> row.id().value()).toList()).executeUpdate().replaceWithVoid()));
    }

    private static void assertPage(IdentifierSchemePageSlice page, List<IdentifierScheme> rows, boolean next, boolean previous) {
        assertEquals(rows.stream().map(IdentifierSchemeResult::fromAggregate).toList(), page.items());
        assertEquals(next ? Optional.of(position(rows.getLast())) : Optional.empty(), page.next());
        assertEquals(previous ? Optional.of(position(rows.getFirst())) : Optional.empty(), page.previous());
    }

    private static IdentifierSchemeSearchCriteria criteria(int limit) {
        return new IdentifierSchemeSearchCriteria("ZZ", null, null, null, limit);
    }

    private static Optional<IdentifierSchemePageBoundary> boundary(IdentifierSchemePageBoundary.Direction direction,
            IdentifierScheme row) {
        return Optional.of(new IdentifierSchemePageBoundary(direction, position(row)));
    }

    private static IdentifierSchemePagePosition position(IdentifierScheme row) {
        return new IdentifierSchemePagePosition(row.auditInfo().createdAt(), row.id());
    }

    private static IdentifierScheme scheme(UUID id, Instant at) {
        return scheme(id, at, "ZZ", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, IdentifierSchemeStatus.RETIRED);
    }

    private static IdentifierScheme scheme(String country, IdentifierCategory category, IdentifierSubjectType subject,
            IdentifierSchemeStatus status) {
        return scheme(UUID.randomUUID(), CREATED, country, category, subject, status);
    }

    private static IdentifierScheme scheme(UUID id, Instant at, String country, IdentifierCategory category,
            IdentifierSubjectType subject, IdentifierSchemeStatus status) {
        return new IdentifierScheme(new IdentifierSchemeId(id), "Page-" + UUID.randomUUID(), country, category, subject,
                "Exact catalog name", null, "HISTORICAL_NORMALIZER_V1", "HISTORICAL_VALIDATOR_V1", null, null, true,
                status, new IdentifierSchemeVersion(9), AuditInfo.initial(at, "original-creator"));
    }

    private static <T> Uni<T> timed(Uni<T> operation) {
        return operation.ifNoItem().after(Duration.ofSeconds(15)).fail();
    }
}
