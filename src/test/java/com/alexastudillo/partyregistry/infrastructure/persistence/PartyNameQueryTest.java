package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyNamePredicate;
import com.alexastudillo.partyregistry.application.model.PartyPageBoundary;
import com.alexastudillo.partyregistry.application.model.PartyPageSlice;
import com.alexastudillo.partyregistry.application.model.PartySearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartySummaryResult;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies multi-batch literal Unicode name filtering, exact metadata, and navigation through the reactive query port.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(90)
class PartyNameQueryTest {

    private static final Instant CREATED = LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atStartOfDay().toInstant(ZoneOffset.UTC);

    @Inject
    Mutiny.SessionFactory sessionFactory;
    @Inject
    PartyQueryPort queries;
    @Inject
    NaturalPersonPersistenceMapper mapper;

    @Test
    @RunOnVertxContext
    void traversesMultipleBatchesWithCanonicalLiteralPredicatesAndExactCounts(UniAsserter asserter) {
        var tenant = new TenantId(UUID.randomUUID());
        List<NaturalPerson> parties = IntStream.range(0, 300).mapToObj(index -> fixture(tenant, index)).toList();
        var criteria = new PartySearchCriteria(PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED,
                new PartyNamePredicate(" straße ", " Águila_% "), CREATED.minusSeconds(299), CREATED, 2);
        var first = new AtomicReference<PartyPageSlice>();
        var second = new AtomicReference<PartyPageSlice>();
        asserter.execute(() -> persist(parties)
                .onFailure(io.smallrye.mutiny.TimeoutException.class)
                .transform(failure -> new AssertionError("Name-query fixture persistence timed out", failure)));
        asserter.assertThat(() -> timed(queries.findPage(tenant, criteria, Optional.empty())), page -> {
            first.set(page);
            assertEquals(150, page.totalElements());
            assertEquals(List.of(parties.get(0).partyId(), parties.get(2).partyId()),
                    page.items().stream().map(PartySummaryResult::partyId).toList());
            assertEquals(parties.getFirst().displayName(), page.items().getFirst().displayName());
            assertTrue(page.previous().isEmpty());
        });
        asserter.assertThat(() -> timed(queries.findPage(tenant, criteria, Optional.of(new PartyPageBoundary(
                PartyPageBoundary.Direction.NEXT, first.get().next().orElseThrow())))), page -> {
                    second.set(page);
                    assertEquals(150, page.totalElements());
                    assertEquals(List.of(parties.get(4).partyId(), parties.get(6).partyId()),
                            page.items().stream().map(PartySummaryResult::partyId).toList());
                });
        asserter.assertThat(() -> timed(queries.findPage(tenant, criteria, Optional.of(new PartyPageBoundary(
                PartyPageBoundary.Direction.PREVIOUS, second.get().previous().orElseThrow())))),
                page -> assertEquals(first.get(), page));
        var noAccents = new PartySearchCriteria(null, null, new PartyNamePredicate("STRASSE", "Aguila_%"), null, null, 50);
        asserter.assertThat(() -> timed(queries.findPage(tenant, noAccents, Optional.empty())), page -> {
            assertEquals(0, page.totalElements());
            assertTrue(page.items().isEmpty());
            assertTrue(page.next().isEmpty());
            assertTrue(page.previous().isEmpty());
        });
    }

    private Uni<Void> persist(List<NaturalPerson> parties) {
        return timed(sessionFactory.withTransaction((session, transaction) -> {
            Uni<Void> sequence = Uni.createFrom().voidItem();
            for (NaturalPerson party : parties) {
                sequence = sequence.call(() -> session.persist(mapper.toEntity(party)));
            }
            return sequence;
        }));
    }

    private static NaturalPerson fixture(TenantId tenant, int index) {
        String label = index % 2 == 0 ? " Straße  Águila_% " + index : "Straße AguilaX_Y " + index;
        return NaturalPerson.restore(new PartyId(UUID.randomUUID()), tenant, label, PartyRecordStatus.ARCHIVED,
                PartyVersion.initial(), AuditInfo.initial(CREATED.minusSeconds(index), "historical-creator"),
                new NaturalPersonDetails("Historical", "Name", null, null, null, "GB"));
    }

    private static <T> Uni<T> timed(Uni<T> operation) {
        return operation.ifNoItem().after(Duration.ofSeconds(30)).fail();
    }
}
