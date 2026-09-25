package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the V6 listing index after comparing V1-V5 EXPLAIN ANALYZE plans on 200 rows. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(60)
class NationalityQueryPlanTest {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    @RunOnVertxContext
    void recordsRepresentativePlanEvidence(UniAsserter asserter) {
        UUID tenant = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        PartyEntity party = new PartyEntity(id, tenant, PartyType.LEGAL_ENTITY, "Query plan fixture",
                PartyRecordStatus.DRAFT, AuditInfo.initial(Instant.parse("2026-09-23T00:00:00Z"), "tester"), 0);
        asserter.execute(() -> sessionFactory.withTransaction((session, transaction) ->
                session.persist(party).call(session::flush).call(() -> session.createNativeQuery("""
                        insert into party_nationalities (party_id, country_code, valid_from, valid_until,
                            created_at, created_by, updated_at, updated_by)
                        select :partyId, 'EC', date '2026-01-01' + n, date '2026-01-01' + n,
                            timestamptz '2026-09-20 00:00:00+00' + n * interval '1 second', 'tester',
                            timestamptz '2026-09-20 00:00:00+00' + n * interval '1 second', 'tester'
                        from generate_series(0, 199) n
                        """).setParameter("partyId", id).executeUpdate()).replaceWithVoid())
                .ifNoItem().after(Duration.ofSeconds(30)).fail());
        asserter.execute(() -> sessionFactory.withSession(session -> session.createNativeQuery(
                "analyze party_nationalities").executeUpdate().replaceWithVoid())
                .ifNoItem().after(Duration.ofSeconds(15)).fail());
        asserter.assertThat(() -> orderedPlan("""
                explain (analyze, buffers)
                select n.id from party_nationalities n join parties p on p.id = n.party_id
                where p.tenant_id = :tenantId and p.id = :partyId and n.valid_from <= date '2026-09-23'
                order by n.created_at desc, n.id desc limit 50
                """, tenant, id, false)
                .ifNoItem().after(Duration.ofSeconds(15)).fail(), plan -> {
                    assertTrue(plan.contains("ix_party_nationalities_party_created_id"), plan);
                });
        asserter.assertThat(() -> sessionFactory.withSession(session -> session.createNativeQuery("""
                explain (analyze, buffers)
                select count(*) from party_nationalities n join parties p on p.id = n.party_id
                where p.tenant_id = :tenantId and p.id = :partyId and n.valid_from <= date '2026-09-23'
                """, String.class).setParameter("tenantId", tenant).setParameter("partyId", id)
                .getResultList().map(lines -> String.join("\n", lines)))
                .ifNoItem().after(Duration.ofSeconds(15)).fail(), plan -> {
                    assertTrue(plan.contains("party_nationalities"), plan);
                });
        asserter.assertThat(() -> orderedPlan("""
                explain (analyze, buffers)
                select n.id from party_nationalities n join parties p on p.id = n.party_id
                where p.tenant_id = :tenantId and p.id = :partyId and n.valid_from <= date '2026-09-23'
                  and (n.created_at < timestamptz '2026-09-20 00:02:00+00'
                       or (n.created_at = timestamptz '2026-09-20 00:02:00+00' and n.id < :boundary))
                order by n.created_at desc, n.id desc limit 50
                """, tenant, id, true)
                .ifNoItem().after(Duration.ofSeconds(15)).fail(), plan -> {
                    assertTrue(plan.contains("ix_party_nationalities_party_created_id"), plan);
                });
    }

    /** Forces an ordered index-capable plan only for this test; the default planner may sort tiny shared fixtures. */
    private Uni<String> orderedPlan(String sql, UUID tenant, UUID id, boolean navigation) {
        return sessionFactory.withTransaction((session, transaction) -> session.createNativeQuery(
                        "set local enable_seqscan = off").executeUpdate()
                .chain(() -> session.createNativeQuery("set local enable_bitmapscan = off").executeUpdate())
                .chain(() -> session.createNativeQuery("set local enable_sort = off").executeUpdate())
                .chain(() -> {
                    var query = session.createNativeQuery(sql, String.class)
                            .setParameter("tenantId", tenant).setParameter("partyId", id);
                    if (navigation) {
                        query.setParameter("boundary", UUID.randomUUID());
                    }
                    return query.getResultList().map(lines -> String.join("\n", lines));
                }));
    }
}
