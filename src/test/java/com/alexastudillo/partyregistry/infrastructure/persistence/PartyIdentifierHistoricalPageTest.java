package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPagePosition;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierPageSlice;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchScope;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierQueryPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies historical keyset pages, exact filters, live continuation and coherent reactive read snapshots. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(120)
class PartyIdentifierHistoricalPageTest {

    private static final LocalDate DATE = LocalDate.of(2025, Month.FEBRUARY, 28);
    private static final Instant CREATED = DATE.atStartOfDay(ZoneOffset.UTC).toInstant().plusNanos(123456000);
    private static final UUID SCHEME = IdentifierSchemeTestFixtures.BOTH_EXPIRING_ID;

    @Inject
    PartyIdentifierQueryPort queries;

    @Inject
    Mutiny.SessionFactory sessions;

    @Test
    @RunOnVertxContext
    void combinesEveryFilterExactlyAndTreatsUnknownOrMismatchedSchemesAsEmpty(UniAsserter asserter) {
        PartyEntity party = party();
        PartyIdentifierEntity matching = identifier(party, SCHEME, PartyIdentifierStatus.VERIFIED, true, CREATED);
        List<PartyIdentifierEntity> rows = List.of(matching,
                identifier(party, SCHEME, PartyIdentifierStatus.VERIFIED, false, CREATED),
                identifier(party, SCHEME, PartyIdentifierStatus.REVOKED, false, CREATED),
                identifier(party, IdentifierSchemeTestFixtures.BOTH_RETIRED_ID, PartyIdentifierStatus.VERIFIED, true, CREATED));
        asserter.execute(() -> persist(party, rows));
        var all = new PartyIdentifierSearchCriteria(new IdentifierSchemeId(SCHEME),
                IdentifierSchemeTestFixtures.BOTH_EXPIRING_CODE, PartyIdentifierStatus.VERIFIED, true, 50);
        asserter.assertThat(() -> page(party, all, null), result -> assertSlice(result, 1, List.of(matching.id())));
        for (PartyIdentifierSearchCriteria none : List.of(
                new PartyIdentifierSearchCriteria(new IdentifierSchemeId(SCHEME), IdentifierSchemeTestFixtures.BOTH_RETIRED_CODE, null, null, 50),
                new PartyIdentifierSearchCriteria(new IdentifierSchemeId(UUID.randomUUID()), null, null, null, 50),
                new PartyIdentifierSearchCriteria(null, "unknown-scheme", null, null, 50),
                new PartyIdentifierSearchCriteria(null, IdentifierSchemeTestFixtures.BOTH_EXPIRING_CODE.toLowerCase(java.util.Locale.ROOT), null, null, 50))) {
            asserter.assertThat(() -> page(party, none, null), result -> assertSlice(result, 0, List.of()));
        }
        asserter.assertThat(() -> page(party, new PartyIdentifierSearchCriteria(null, null, null, false, 50), null),
                result -> assertEquals(2, result.totalElements()));
    }

    @Test
    @RunOnVertxContext
    void navigatesForwardDescendingMicrosecondAndUnsignedUuidTies(UniAsserter asserter) {
        PartyEntity party = party();
        List<PartyIdentifierEntity> rows = history(party);
        List<UUID> ordered = orderedIds(rows);
        asserter.execute(() -> persist(party, rows.reversed()));
        asserter.assertThat(() -> page(party, unfiltered(2), null), first -> {
            assertSlice(first, 5, ordered.subList(0, 2));
            assertTrue(first.next().isPresent());
            assertTrue(first.previous().isEmpty());
            asserter.putData("next", first.next().orElseThrow());
        });
        asserter.assertThat(() -> page(party, unfiltered(2), next(asserter)), middle -> {
            assertSlice(middle, 5, ordered.subList(2, 4));
            assertTrue(middle.next().isPresent());
            assertTrue(middle.previous().isPresent());
            asserter.putData("next", middle.next().orElseThrow());
        });
        asserter.assertThat(() -> page(party, unfiltered(2), next(asserter)), last -> {
            assertSlice(last, 5, ordered.subList(4, 5));
            assertTrue(last.next().isEmpty());
            assertTrue(last.previous().isPresent());
        });
    }

    @Test
    @RunOnVertxContext
    void backwardTraversalRecoversNearestPriorPagesInDescendingOrder(UniAsserter asserter) {
        PartyEntity party = party();
        List<PartyIdentifierEntity> rows = history(party);
        List<UUID> ordered = orderedIds(rows);
        asserter.execute(() -> persist(party, rows));
        var afterLast = new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.PREVIOUS,
                new PartyIdentifierPagePosition(CREATED.minusSeconds(1), new PartyIdentifierId(UUID.randomUUID())));
        asserter.assertThat(() -> page(party, unfiltered(2), afterLast), tail -> {
            assertSlice(tail, 5, ordered.subList(3, 5));
            asserter.putData("previous", tail.previous().orElseThrow());
        });
        asserter.assertThat(() -> page(party, unfiltered(2), previous(asserter)), middle -> {
            assertSlice(middle, 5, ordered.subList(1, 3));
            asserter.putData("previous", middle.previous().orElseThrow());
        });
        asserter.assertThat(() -> page(party, unfiltered(2), previous(asserter)), first -> {
            assertSlice(first, 5, ordered.subList(0, 1));
            assertTrue(first.previous().isEmpty());
            assertTrue(first.next().isPresent());
        });
    }

    @Test
    @RunOnVertxContext
    void continuedEmptyPagesRetainTotalsAndOnlyRealNeighborDirections(UniAsserter asserter) {
        PartyEntity party = party();
        asserter.execute(() -> persist(party, history(party)));
        var beyondEnd = new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.NEXT,
                new PartyIdentifierPagePosition(CREATED.minusSeconds(1), new PartyIdentifierId(UUID.randomUUID())));
        var beforeStart = new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.PREVIOUS,
                new PartyIdentifierPagePosition(CREATED.plusSeconds(1), new PartyIdentifierId(UUID.randomUUID())));
        asserter.assertThat(() -> page(party, unfiltered(2), beyondEnd), page -> {
            assertSlice(page, 5, List.of());
            assertTrue(page.next().isEmpty());
            assertTrue(page.previous().isPresent());
        });
        asserter.assertThat(() -> page(party, unfiltered(2), beforeStart), page -> {
            assertSlice(page, 5, List.of());
            assertTrue(page.previous().isEmpty());
            assertTrue(page.next().isPresent());
        });
    }

    @Test
    @RunOnVertxContext
    void deletingBoundaryDoesNotInvalidateLiveContinuation(UniAsserter asserter) {
        PartyEntity party = party();
        List<PartyIdentifierEntity> rows = history(party);
        List<UUID> ordered = orderedIds(rows);
        asserter.execute(() -> persist(party, rows));
        asserter.assertThat(() -> page(party, unfiltered(2), null), page -> asserter.putData("next", page.next().orElseThrow()));
        asserter.execute(() -> delete(ordered.get(1)));
        asserter.assertThat(() -> page(party, unfiltered(2), next(asserter)), page -> assertSlice(page, 4, ordered.subList(2, 4)));
    }

    @Test
    @RunOnVertxContext
    void changingBoundaryFilterMembershipPreservesItsContinuationTuple(UniAsserter asserter) {
        PartyEntity party = party();
        List<PartyIdentifierEntity> rows = List.of(
                identifier(party, SCHEME, PartyIdentifierStatus.VERIFIED, false, CREATED),
                identifier(party, SCHEME, PartyIdentifierStatus.VERIFIED, false, CREATED),
                identifier(party, SCHEME, PartyIdentifierStatus.VERIFIED, false, CREATED));
        List<UUID> ordered = orderedIds(rows);
        var verified = new PartyIdentifierSearchCriteria(null, null, PartyIdentifierStatus.VERIFIED, null, 1);
        asserter.execute(() -> persist(party, rows));
        asserter.assertThat(() -> page(party, verified, null), page -> asserter.putData("next", page.next().orElseThrow()));
        asserter.execute(() -> timed(sessions.withTransaction((session, transaction) -> session.createMutationQuery(
                "update PartyIdentifierEntity set status = :status, version = version + 1 where id = :id")
                .setParameter("status", PartyIdentifierStatus.REVOKED).setParameter("id", ordered.getFirst()).executeUpdate().replaceWithVoid())));
        asserter.assertThat(() -> page(party, verified, next(asserter)), page -> assertSlice(page, 2, ordered.subList(1, 2)));
    }

    @Test
    @RunOnVertxContext
    void emptyAndWrongOwnerScopesReturnBoundedZeroSlices(UniAsserter asserter) {
        PartyEntity party = party();
        PartyIdentifierEntity identifier = identifier(party, SCHEME, PartyIdentifierStatus.PENDING_VERIFICATION, false, CREATED);
        asserter.execute(() -> persist(party, List.of(identifier)));
        RequestMetadata foreign = new RequestMetadata(new TenantId(UUID.randomUUID()), "reader", UUID.randomUUID());
        var scope = new PartyIdentifierSearchScope(foreign.tenantId(), new PartyId(party.id()), unfiltered(50));
        asserter.assertThat(() -> timed(queries.findPage(foreign, scope, null)), page -> {
            assertSlice(page, 0, List.of());
            assertTrue(page.next().isEmpty());
            assertTrue(page.previous().isEmpty());
        });
        asserter.assertThat(() -> page(party, new PartyIdentifierSearchCriteria(null, null, PartyIdentifierStatus.EXPIRED, null, 50), null),
                page -> assertSlice(page, 0, List.of()));
    }

    @Test
    @RunOnVertxContext
    void countRowsAndNeighborsUseOneReadOnlyRepeatableReadView(UniAsserter asserter) {
        PartyEntity party = party();
        PartyIdentifierEntity identifier = identifier(party, SCHEME, PartyIdentifierStatus.VERIFIED, false, CREATED);
        asserter.execute(() -> persist(party, List.of(identifier)));
        var sql = new PartyIdentifierPageSql(scope(party, unfiltered(2)));
        asserter.assertThat(() -> timed(sessions.withTransaction((session, transaction) ->
                session.createNativeQuery(HibernateReactivePartyIdentifierQueryAdapter.SNAPSHOT_TRANSACTION).executeUpdate()
                        .flatMap(ignored -> session.createNativeQuery("SELECT current_setting('transaction_isolation') || '/' || current_setting('transaction_read_only')", String.class).getSingleResult())
                        .invoke(settings -> assertEquals("repeatable read/on", settings))
                        .flatMap(ignored -> sql.count(session))
                        .invoke(total -> assertEquals(1, total))
                        .call(() -> deleteInIndependentSession(identifier.id()))
                        .flatMap(ignored -> sql.page(session, null)))), rows -> assertEquals(List.of(identifier.id()),
                rows.stream().map(row -> row.identifierId().value()).toList()));
        asserter.assertThat(() -> page(party, unfiltered(2), null), page -> assertSlice(page, 0, List.of()));
    }

    @Test
    @RunOnVertxContext
    void recordsRepresentativeBoundedHistoricalOrderingPlan(UniAsserter asserter) {
        PartyEntity party = party();
        asserter.execute(() -> persist(party, history(party)));
        asserter.assertThat(() -> timed(sessions.withSession(session -> session.createNativeQuery("""
                EXPLAIN (ANALYZE, BUFFERS) SELECT i.id, i.created_at
                FROM party_identifiers i WHERE i.tenant_id = :tenant AND i.party_id = :party
                ORDER BY i.created_at DESC, i.id DESC LIMIT 2
                """, String.class).setParameter("tenant", party.tenantId()).setParameter("party", party.id()).getResultList())), lines -> {
            String plan = String.join("\n", lines);
            assertTrue(plan.contains("Limit"));
            assertTrue(plan.contains("actual time"));
            Logger.getLogger(PartyIdentifierHistoricalPageTest.class).infof("Synthetic historical listing plan: %s", plan);
        });
    }

    private Uni<PartyIdentifierPageSlice> page(PartyEntity party, PartyIdentifierSearchCriteria criteria, PartyIdentifierPageBoundary boundary) {
        RequestMetadata metadata = new RequestMetadata(new TenantId(party.tenantId()), "reader", UUID.randomUUID());
        return timed(queries.findPage(metadata, scope(party, criteria), boundary));
    }

    private static PartyIdentifierSearchScope scope(PartyEntity party, PartyIdentifierSearchCriteria criteria) {
        return new PartyIdentifierSearchScope(new TenantId(party.tenantId()), new PartyId(party.id()), criteria);
    }

    private static PartyIdentifierSearchCriteria unfiltered(int limit) {
        return new PartyIdentifierSearchCriteria(null, null, null, null, limit);
    }

    private static PartyIdentifierPageBoundary next(UniAsserter asserter) {
        return new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.NEXT,
                (PartyIdentifierPagePosition) asserter.getData("next"));
    }

    private static PartyIdentifierPageBoundary previous(UniAsserter asserter) {
        return new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.PREVIOUS,
                (PartyIdentifierPagePosition) asserter.getData("previous"));
    }

    private static void assertSlice(PartyIdentifierPageSlice slice, long total, List<UUID> ids) {
        assertEquals(total, slice.totalElements());
        assertEquals(ids, slice.items().stream().map(item -> item.identifierId().value()).toList());
        assertFalse(slice.items().toString().contains("synthetic-page-ciphertext"));
    }

    private Uni<Void> persist(PartyEntity party, List<PartyIdentifierEntity> identifiers) {
        return timed(sessions.withTransaction((session, transaction) -> {
            Uni<Void> work = session.persist(party);
            for (PartyIdentifierEntity identifier : identifiers) {
                work = work.call(() -> session.persist(identifier));
            }
            return work.call(session::flush);
        }));
    }

    private Uni<Void> delete(UUID id) {
        return timed(sessions.withTransaction((session, transaction) -> session.createMutationQuery(
                "delete from PartyIdentifierEntity where id = :id").setParameter("id", id).executeUpdate().replaceWithVoid()));
    }

    private Uni<Void> deleteInIndependentSession(UUID id) {
        return sessions.openSession().flatMap(session -> session.withTransaction(transaction -> session.createMutationQuery(
                "delete from PartyIdentifierEntity where id = :id").setParameter("id", id).executeUpdate().replaceWithVoid()).eventually(session::close));
    }

    private static PartyEntity party() {
        AuditInfo audit = AuditInfo.initial(CREATED, "creator");
        PartyEntity party = new PartyEntity(UUID.randomUUID(), UUID.randomUUID(), PartyType.NATURAL_PERSON,
                "Historical page owner", PartyRecordStatus.ARCHIVED, audit, 4);
        party.attachNaturalPersonDetails(new NaturalPersonDetailsEntity(party.id(),
                new NaturalPersonDetails("Page", "Owner", null, null, null, null), audit));
        return party;
    }

    private static List<PartyIdentifierEntity> history(PartyEntity party) {
        List<PartyIdentifierEntity> rows = new ArrayList<>();
        for (PartyIdentifierStatus status : PartyIdentifierStatus.values()) {
            Instant created = switch (status) {
                case PENDING_VERIFICATION -> CREATED.plusNanos(1000);
                case REVOKED -> CREATED.minusNanos(1000);
                default -> CREATED;
            };
            rows.add(identifier(party, SCHEME, status, false, created));
        }
        return rows;
    }

    private static List<UUID> orderedIds(List<PartyIdentifierEntity> rows) {
        return rows.stream().sorted(Comparator.comparing(PartyIdentifierEntity::createdAt)
                .thenComparing(row -> row.id().toString()).reversed()).map(PartyIdentifierEntity::id).toList();
    }

    private static PartyIdentifierEntity identifier(PartyEntity party, UUID scheme, PartyIdentifierStatus status,
            boolean primary, Instant created) {
        boolean verified = status == PartyIdentifierStatus.VERIFIED || status == PartyIdentifierStatus.REVOKED;
        return PartyIdentifierEntity.builder().id(UUID.randomUUID()).tenantId(party.tenantId()).partyId(party.id())
                .identifierSchemeId(scheme).issuerCode(null).encryptedValue("synthetic-page-ciphertext")
                .encryptionKeyVersion((short) 1).normalizedValueHash(UUID.randomUUID().toString().replace("-", "").repeat(2))
                .maskedValue("***1234").normalizationVersion((short) 1).status(status).primary(primary)
                .issuedOn(DATE.minusYears(1)).expiresOn(DATE.minusDays(1))
                .verifiedAt(verified ? created : null).verifiedBy(verified ? "verifier" : null)
                .createdAt(created).createdBy("creator").updatedAt(created).updatedBy("creator").version(0).build();
    }

    private static <T> Uni<T> timed(Uni<T> work) {
        return work.ifNoItem().after(Duration.ofSeconds(15)).fail();
    }
}
