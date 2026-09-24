package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyNamePredicate;
import com.alexastudillo.partyregistry.application.model.PartyPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyPagePosition;
import com.alexastudillo.partyregistry.application.model.PartyPageSlice;
import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartySummaryResult;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
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
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the query port's tenant-safe structural pages, live boundaries, and exact temporal ordering.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(60)
class PartyPageReaderTest {

    private static final Instant CREATED = LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atTime(12, 0, 0, 123456000).toInstant(ZoneOffset.UTC);
    private static final PartyNamePredicate NO_NAME = new PartyNamePredicate(null, null);

    @Inject
    Mutiny.SessionFactory sessionFactory;
    @Inject
    PartyQueryPort reader;
    @Inject
    NaturalPersonPersistenceMapper naturalMapper;
    @Inject
    LegalEntityPersistenceMapper legalMapper;

    @Test
    @RunOnVertxContext
    void traversesStableTimestampTiesAndRestoresThePreviousPage(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        List<Party> parties = List.of(
                natural(tenant, new UUID(Long.MIN_VALUE, UUID.randomUUID().getLeastSignificantBits()), CREATED),
                natural(tenant, new UUID(Long.MAX_VALUE, UUID.randomUUID().getLeastSignificantBits()), CREATED),
                natural(tenant, UUID.randomUUID(), CREATED.minusNanos(1000)),
                natural(tenant, UUID.randomUUID(), CREATED.minusNanos(2000)),
                natural(tenant, UUID.randomUUID(), CREATED.minusNanos(3000)));
        var criteria = new PartySearchCriteria(null, null, NO_NAME, null, null, 2);
        asserter.execute(() -> persist(parties));
        asserter.assertThat(() -> read(tenant, criteria, Optional.empty()), page -> {
            assertPage(page, parties.subList(0, 2), 5);
            assertEquals(Optional.of(position(parties.get(1))), page.next());
            assertTrue(page.previous().isEmpty());
        });
        asserter.assertThat(() -> read(tenant, criteria, next(parties.get(1))), page -> {
            assertPage(page, parties.subList(2, 4), 5);
            assertEquals(Optional.of(position(parties.get(3))), page.next());
            assertEquals(Optional.of(position(parties.get(2))), page.previous());
        });
        var backwards = Optional.of(new PartyPageBoundary(PartyPageBoundary.Direction.PREVIOUS, position(parties.get(2))));
        asserter.assertThat(() -> read(tenant, criteria, backwards), page -> assertPage(page, parties.subList(0, 2), 5));
        asserter.assertThat(() -> read(tenant, criteria, next(parties.get(3))), page -> {
            assertPage(page, parties.subList(4, 5), 5);
            assertTrue(page.next().isEmpty());
            assertEquals(Optional.of(position(parties.get(4))), page.previous());
        });
    }

    @Test
    @RunOnVertxContext
    void intersectsTypeStatusAndInclusiveInstantBoundsWithoutDisclosingAnotherTenant(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        var otherTenant = new TenantId(UUID.randomUUID());
        Party natural = natural(tenant, UUID.randomUUID(), CREATED);
        Party legal = legal(tenant, PartyRecordStatus.ACTIVE, CREATED.plusNanos(1000));
        Party archived = legal(tenant, PartyRecordStatus.ARCHIVED, CREATED.plusNanos(2000));
        asserter.execute(() -> persist(List.of(natural, legal, archived, legal(otherTenant, PartyRecordStatus.ACTIVE, CREATED))));
        var exact = new PartySearchCriteria(PartyType.LEGAL_ENTITY, PartyRecordStatus.ACTIVE, NO_NAME,
                CREATED.plusNanos(1000), CREATED.plusNanos(1000), 200);
        asserter.assertThat(() -> read(tenant, exact, Optional.empty()), page -> assertPage(page, List.of(legal), 1));
        var between = new PartySearchCriteria(null, null, NO_NAME, CREATED.plusNanos(1), CREATED.plusNanos(999), 50);
        asserter.assertThat(() -> read(tenant, between, Optional.empty()), page -> {
            assertPage(page, List.of(), 0);
            assertTrue(page.next().isEmpty());
            assertTrue(page.previous().isEmpty());
        });
        var inclusive = new PartySearchCriteria(null, null, NO_NAME, CREATED, CREATED.plusNanos(1000), 50);
        asserter.assertThat(() -> read(tenant, inclusive, Optional.empty()),
                page -> assertPage(page, List.of(legal, natural), 2));
    }

    @Test
    @RunOnVertxContext
    void continuesFromAChangedBoundaryAndDescribesEmptyContinuationAgainstCurrentData(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        Party first = natural(tenant, UUID.randomUUID(), CREATED);
        Party middle = natural(tenant, UUID.randomUUID(), CREATED.minusNanos(1000));
        Party last = natural(tenant, UUID.randomUUID(), CREATED.minusNanos(2000));
        var criteria = new PartySearchCriteria(null, PartyRecordStatus.DRAFT, NO_NAME, null, null, 1);
        asserter.execute(() -> persist(List.of(first, middle, last)));
        asserter.execute(() -> changeStatus(middle));
        asserter.assertThat(() -> read(tenant, criteria, next(middle)), page -> assertPage(page, List.of(last), 2));
        asserter.execute(() -> changeStatus(last));
        asserter.assertThat(() -> read(tenant, criteria, next(middle)), page -> {
            assertPage(page, List.of(), 1);
            assertTrue(page.next().isEmpty());
            assertEquals(Optional.of(position(middle)), page.previous());
        });
    }

    private Uni<Void> changeStatus(Party party) {
        return timed(sessionFactory.withTransaction((session, transaction) -> session.createMutationQuery("""
                update PartyEntity set recordStatus = :status, version = version + 1
                where tenantId = :tenant and id = :id
                """).setParameter("status", PartyRecordStatus.ACTIVE).setParameter("tenant", party.tenantId().value())
                .setParameter("id", party.partyId().value()).executeUpdate().replaceWithVoid()));
    }

    private Uni<PartyPageSlice> read(TenantId tenant, PartySearchCriteria criteria, Optional<PartyPageBoundary> boundary) {
        return timed(reader.findPage(tenant, criteria, boundary));
    }

    private Uni<Void> persist(List<Party> parties) {
        return timed(sessionFactory.withTransaction((session, transaction) -> {
            Uni<Void> sequence = Uni.createFrom().voidItem();
            for (Party party : parties) {
                PartyEntity entity = switch (party) {
                    case NaturalPerson natural -> naturalMapper.toEntity(natural);
                    case LegalEntity legal -> legalMapper.toEntity(legal);
                };
                sequence = sequence.call(() -> session.persist(entity));
            }
            return sequence;
        }));
    }

    private static void assertPage(PartyPageSlice page, List<Party> parties, long total) {
        assertEquals(parties.stream().map(party -> new PartySummaryResult(party.partyId(), party.type(), party.displayName(),
                party.recordStatus(), party.auditInfo().createdAt(), party.version())).toList(), page.items());
        assertEquals(total, page.totalElements());
    }

    private static Optional<PartyPageBoundary> next(Party party) {
        return Optional.of(new PartyPageBoundary(PartyPageBoundary.Direction.NEXT, position(party)));
    }

    private static PartyPagePosition position(Party party) {
        return new PartyPagePosition(party.auditInfo().createdAt(), party.partyId());
    }

    private static Party natural(TenantId tenant, UUID id, Instant createdAt) {
        return NaturalPerson.restore(new PartyId(id), tenant, "Historical Label", PartyRecordStatus.DRAFT,
                PartyVersion.initial(), AuditInfo.initial(createdAt, "creator"),
                new NaturalPersonDetails("Historical", "Name", null, null, null, "GB"));
    }

    private static Party legal(TenantId tenant, PartyRecordStatus status, Instant createdAt) {
        return LegalEntity.restore(new PartyId(UUID.randomUUID()), tenant, "Historical Company", status,
                PartyVersion.initial(), AuditInfo.initial(createdAt, "creator"),
                new LegalEntityDetails("Historical Company", null, null, "GB", null, null));
    }

    private static <T> Uni<T> timed(Uni<T> operation) {
        return operation.ifNoItem().after(Duration.ofSeconds(15)).fail();
    }
}
