package com.alexastudillo.partyregistry.infrastructure.persistence;

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
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises qualified detached reads for both Party types and retained
 * lifecycle states.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(60)
class HibernateReactiveNationalityReadAdapterTest {

    private static final Instant CREATED = Instant.parse("2026-09-01T10:00:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Inject
    NationalityReadPort readPort;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    @RunOnVertxContext
    void checksPartyOwnershipAndConcealsCrossPartyOrTenantNationalityIds(UniAsserter asserter) {
        UUID tenant = UUID.randomUUID();
        PartyEntity natural = party(tenant, PartyType.NATURAL_PERSON, PartyRecordStatus.ARCHIVED);
        PartyEntity legal = party(tenant, PartyType.LEGAL_ENTITY, PartyRecordStatus.DRAFT);
        PartyEntity foreign = party(UUID.randomUUID(), PartyType.NATURAL_PERSON, PartyRecordStatus.ACTIVE);
        PartyNationalityEntity ended = nationality(natural, "EC", LocalDate.of(2020, Month.JANUARY, 1),
                LocalDate.of(2020, Month.DECEMBER, 31));
        PartyNationalityEntity future = nationality(legal, "CO", LocalDate.of(2030, Month.JANUARY, 1), null);
        PartyNationalityEntity concealed = nationality(foreign, "US", null, null);

        asserter.execute(() -> sessionFactory.withTransaction((session, transaction) -> {
            io.smallrye.mutiny.Uni<Void> sequence = io.smallrye.mutiny.Uni.createFrom().voidItem();
            for (PartyEntity party : List.of(natural, legal, foreign)) {
                sequence = sequence.call(() -> session.persist(party));
            }
            for (PartyNationalityEntity nationality : List.of(ended, future, concealed)) {
                sequence = sequence.call(() -> session.persist(nationality));
            }
            return sequence.call(session::flush);
        }).ifNoItem().after(TIMEOUT).fail());

        TenantId ownedTenant = new TenantId(tenant);
        asserter.assertThat(() -> readPort.partyExists(ownedTenant, new PartyId(natural.id())),
                Assertions::assertTrue);
        asserter.assertThat(() -> readPort.partyExists(ownedTenant, new PartyId(legal.id())),
                Assertions::assertTrue);
        asserter.assertThat(() -> readPort.partyExists(ownedTenant, new PartyId(foreign.id())),
                Assertions::assertFalse);
        asserter.assertThat(() -> readPort.findById(ownedTenant, new PartyId(natural.id()),
                new NationalityId(ended.id())), result -> {
                    assertEquals("EC", result.orElseThrow().countryCode());
                    assertEquals(LocalDate.of(2020, Month.DECEMBER, 31), result.orElseThrow().validUntil());
                });
        asserter.assertThat(() -> readPort.findById(ownedTenant, new PartyId(legal.id()),
                new NationalityId(future.id())),
                result -> assertEquals(LocalDate.of(2030, Month.JANUARY, 1), result.orElseThrow().validFrom()));
        asserter.assertThat(() -> readPort.findById(ownedTenant, new PartyId(natural.id()),
                new NationalityId(future.id())), result -> assertTrue(result.isEmpty()));
        asserter.assertThat(() -> readPort.findById(ownedTenant, new PartyId(natural.id()),
                new NationalityId(concealed.id())), result -> assertTrue(result.isEmpty()));
        asserter.assertThat(() -> readPort.findById(ownedTenant, new PartyId(foreign.id()),
                new NationalityId(concealed.id())), result -> assertTrue(result.isEmpty()));
    }

    private static PartyEntity party(UUID tenant, PartyType type, PartyRecordStatus status) {
        return new PartyEntity(UUID.randomUUID(), tenant, type, "Nationality read fixture", status,
                AuditInfo.initial(CREATED, "creator"), 0);
    }

    private static PartyNationalityEntity nationality(PartyEntity party, String country,
            LocalDate from, LocalDate until) {
        return new PartyNationalityEntity(new PartyNationality(
                new NationalityId(UUID.randomUUID()), new PartyId(party.id()), country, false,
                new NationalityPeriod(from, until), AuditInfo.initial(CREATED, "creator")));
    }
}
