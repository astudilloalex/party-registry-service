package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.NationalityPageBoundary;
import com.alexastudillo.partyregistry.application.model.NationalityPageSlice;
import com.alexastudillo.partyregistry.application.model.NationalitySearchCriteria;
import com.alexastudillo.partyregistry.application.port.NationalityReadPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.NationalityPeriod;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyNationality;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises snapshot count, temporal filters, exact UUID ties, and
 * bidirectional keyset pages.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(60)
class HibernateReactiveNationalityPageTest {

    private static final LocalDate TODAY = LocalDate.of(2026, Month.SEPTEMBER, 23);
    private static final Instant CREATED = Instant.parse("2026-09-20T10:00:00.123456Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Inject
    NationalityReadPort readPort;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    @RunOnVertxContext
    void filtersAndTraversesTiedTimestampsWithoutSkippingOrRecounting(UniAsserter asserter) {
        PartyEntity party = new PartyEntity(UUID.randomUUID(), UUID.randomUUID(), PartyType.LEGAL_ENTITY,
                "Page fixture", PartyRecordStatus.ARCHIVED, AuditInfo.initial(CREATED, "tester"), 0);
        PartyEntity other = new PartyEntity(UUID.randomUUID(), UUID.randomUUID(), PartyType.NATURAL_PERSON,
                "Other fixture", PartyRecordStatus.DRAFT, AuditInfo.initial(CREATED, "tester"), 0);
        PartyNationalityEntity currentLow = nationality(party, "EC", true, null, null,
                UUID.fromString("00000000-0000-4000-8000-000000000001"), CREATED);
        PartyNationalityEntity currentHigh = nationality(party, "CO", false, null, null,
                UUID.fromString("80000000-0000-4000-8000-000000000001"), CREATED);
        PartyNationalityEntity expired = nationality(party, "US", false, null, TODAY.minusDays(1),
                UUID.randomUUID(), CREATED.minusSeconds(1));
        PartyNationalityEntity future = nationality(party, "MX", false, TODAY.plusDays(1), null,
                UUID.randomUUID(), CREATED.plusSeconds(1));
        PartyNationalityEntity concealed = nationality(other, "PE", false, null, null,
                UUID.randomUUID(), CREATED.plusSeconds(2));
        TenantId tenant = new TenantId(party.tenantId());
        PartyId id = new PartyId(party.id());

        asserter.execute(
                () -> sessionFactory
                        .withTransaction((session, transaction) -> session.persist(party)
                                .call(() -> session.persist(other))
                                .call(() -> session.persist(currentLow))
                                .call(() -> session.persist(currentHigh))
                                .call(() -> session.persist(expired))
                                .call(() -> session.persist(future))
                                .call(() -> session.persist(concealed))
                                .call(session::flush))
                        .ifNoItem().after(TIMEOUT).fail());
        NationalitySearchCriteria current = criteria(null, null, false, 1);
        asserter.assertThat(() -> page(tenant, id, current, Optional.empty()),
                first -> assertSlice(first, 2, List.of(currentHigh.id()), true, false));
        asserter.assertThat(() -> page(tenant, id, current, Optional.empty())
                .flatMap(first -> page(tenant, id, current, Optional.of(
                        new NationalityPageBoundary(NationalityPageBoundary.Direction.NEXT,
                                first.next().orElseThrow())))),
                second -> assertSlice(second, 2, List.of(currentLow.id()), false, true));
        asserter.assertThat(() -> page(tenant, id, current, Optional.empty())
                .flatMap(first -> page(tenant, id, current, Optional.of(
                        new NationalityPageBoundary(NationalityPageBoundary.Direction.NEXT,
                                first.next().orElseThrow()))))
                .flatMap(second -> page(tenant, id, current, Optional.of(
                        new NationalityPageBoundary(NationalityPageBoundary.Direction.PREVIOUS,
                                second.previous().orElseThrow())))),
                previous -> assertEquals(List.of(currentHigh.id()), ids(previous)));
        asserter.assertThat(() -> page(tenant, id, criteria(null, null, true, 50), Optional.empty()),
                all -> assertSlice(all, 3, List.of(currentHigh.id(), currentLow.id(), expired.id()),
                        false, false));
        asserter.assertThat(() -> page(tenant, id, criteria("EC", true, false, 50), Optional.empty()),
                selected -> assertEquals(List.of(currentLow.id()), ids(selected)));
        asserter.assertThat(() -> page(tenant, id, criteria("CO", true, true, 50), Optional.empty()),
                HibernateReactiveNationalityPageTest::assertEmptySlice);
        asserter.assertThat(() -> readPort.findPage(tenant, new PartyId(other.id()), current, Optional.empty()),
                absent -> assertFalse(absent.isPresent()));
    }

    private static void assertSlice(NationalityPageSlice slice, long totalElements, List<UUID> expectedIds,
            boolean hasNext, boolean hasPrevious) {
        assertEquals(totalElements, slice.totalElements());
        assertEquals(expectedIds, ids(slice));
        assertEquals(hasNext, slice.next().isPresent());
        assertEquals(hasPrevious, slice.previous().isPresent());
    }

    private static void assertEmptySlice(NationalityPageSlice slice) {
        assertTrue(slice.items().isEmpty());
        assertEquals(0, slice.totalElements());
        assertTrue(slice.next().isEmpty());
        assertTrue(slice.previous().isEmpty());
    }

    private Uni<NationalityPageSlice> page(TenantId tenant, PartyId party,
            NationalitySearchCriteria criteria, Optional<NationalityPageBoundary> boundary) {
        return readPort.findPage(tenant, party, criteria, boundary).map(Optional::orElseThrow)
                .ifNoItem().after(TIMEOUT).fail();
    }

    private static NationalitySearchCriteria criteria(String country, Boolean primary, boolean expired, int limit) {
        return new NationalitySearchCriteria(country, primary, TODAY, expired, limit);
    }

    private static List<UUID> ids(NationalityPageSlice slice) {
        return slice.items().stream().map(item -> item.nationalityId().value()).toList();
    }

    private static PartyNationalityEntity nationality(PartyEntity party, String country, boolean primary,
            LocalDate from, LocalDate until, UUID id, Instant createdAt) {
        return new PartyNationalityEntity(new PartyNationality(new NationalityId(id), new PartyId(party.id()),
                country, primary, new NationalityPeriod(from, until),
                AuditInfo.initial(createdAt, "tester")));
    }
}
