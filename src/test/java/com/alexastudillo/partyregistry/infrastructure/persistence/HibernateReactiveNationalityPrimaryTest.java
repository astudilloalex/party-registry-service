package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.SetPrimaryNationalityCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.NationalityMutationPort;
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
import com.alexastudillo.partyregistry.support.RootPartyFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests date-qualified primary transfers, unaffected history, and unkeyed no-op
 * audit.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(60)
class HibernateReactiveNationalityPrimaryTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, Month.FEBRUARY, 10);

    @Inject
    NationalityMutationPort mutations;

    @Inject
    NationalityReadPort reads;

    @Inject
    Mutiny.SessionFactory sessions;

    @Test
    void demotesOnlyIntersectingPrimaryAndPreservesNoopAuditAndVersion() {
        PartyEntity party = party();
        PartyNationalityEntity historical = nationality(party, "US", true,
                LocalDate.of(2025, Month.JANUARY, 1), LocalDate.of(2025, Month.DECEMBER, 31));
        PartyNationalityEntity former = nationality(party, "EC", true,
                LocalDate.of(2026, Month.JANUARY, 1), LocalDate.of(2026, Month.FEBRUARY, 15));
        PartyNationalityEntity target = nationality(party, "CO", false,
                LocalDate.of(2026, Month.FEBRUARY, 1), LocalDate.of(2026, Month.MARCH, 31));
        seed(party, List.of(historical, former, target));
        var oldHistory = read(party, historical);
        var oldFormer = read(party, former);
        var oldTarget = read(party, target);

        var applied = RootPartyFixtures.await(() -> mutations.setPrimary(command(party, target)));
        assertEquals(true, applied.nationality().isPrimary());
        assertEquals(oldTarget.createdAt(), applied.nationality().createdAt());
        assertEquals(oldTarget.validFrom(), applied.nationality().validFrom());
        assertEquals(oldTarget.validUntil(), applied.nationality().validUntil());
        assertTrue(applied.nationality().updatedAt().isAfter(oldTarget.updatedAt()));
        var updatedFormer = read(party, former);
        assertFalse(updatedFormer.isPrimary());
        assertEquals(oldFormer.validFrom(), updatedFormer.validFrom());
        assertEquals(oldFormer.validUntil(), updatedFormer.validUntil());
        assertTrue(updatedFormer.updatedAt().isAfter(oldFormer.updatedAt()));
        assertEquals(oldHistory, read(party, historical));
        assertEquals(1L, version(party));

        var repeated = RootPartyFixtures.await(() -> mutations.setPrimary(command(party, target)));
        assertEquals(applied.nationality(), repeated.nationality());
        assertEquals(updatedFormer, read(party, former));
        assertEquals(1L, version(party));
    }

    @Test
    void rejectsFutureAndEndedTargetsWithoutDemotionOrVersionChange() {
        PartyEntity party = party();
        PartyNationalityEntity ended = nationality(party, "EC", false,
                LocalDate.of(2025, Month.JANUARY, 1), TODAY.minusDays(1));
        PartyNationalityEntity future = nationality(party, "CO", false, TODAY.plusDays(1), null);
        seed(party, List.of(ended, future));
        for (PartyNationalityEntity target : List.of(ended, future)) {
            var original = read(party, target);
            ApplicationException failure = org.junit.jupiter.api.Assertions.assertThrows(
                    ApplicationException.class,
                    () -> RootPartyFixtures
                            .await(() -> mutations.setPrimary(command(party, target))));
            assertInstanceOf(ApplicationFailure.NationalityNotEffective.class, failure.failure());
            assertEquals(original, read(party, target));
        }
        assertEquals(0L, version(party));
    }

    @Test
    void keyedReplayPreservesOriginalResultAfterAnotherTransferAndRejectsChangedTarget() {
        PartyEntity party = party();
        PartyNationalityEntity first = nationality(party, "EC", false, TODAY.minusDays(1), null);
        PartyNationalityEntity second = nationality(party, "CO", false, TODAY.minusDays(1), null);
        PartyNationalityEntity future = nationality(party, "US", false, TODAY.plusDays(1), null);
        seed(party, List.of(first, second, future));

        var original = RootPartyFixtures.await(() -> mutations.setPrimary(keyed(party, first, "primary-key")));
        assertEquals(1L, version(party));
        RootPartyFixtures.await(() -> mutations.setPrimary(command(party, second)));
        assertFalse(read(party, first).isPrimary());
        assertTrue(read(party, second).isPrimary());
        assertEquals(2L, version(party));

        var replay = RootPartyFixtures.await(() -> mutations.setPrimary(keyed(party, first, "primary-key")));
        assertEquals(com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome.Disposition.REPLAYED,
                replay.disposition());
        assertEquals(original.nationality(), replay.nationality());
        assertFalse(read(party, first).isPrimary());
        assertEquals(2L, version(party));
        ApplicationException changed = org.junit.jupiter.api.Assertions.assertThrows(ApplicationException.class,
                () -> RootPartyFixtures.await(
                        () -> mutations.setPrimary(keyed(party, second, "primary-key"))));
        assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, changed.failure());

        ApplicationException ineffective = org.junit.jupiter.api.Assertions.assertThrows(
                ApplicationException.class,
                () -> RootPartyFixtures
                        .await(() -> mutations.setPrimary(keyed(party, future, "failed-key"))));
        assertInstanceOf(ApplicationFailure.NationalityNotEffective.class, ineffective.failure());
        var accepted = RootPartyFixtures.await(() -> mutations.setPrimary(keyed(party, second, "failed-key")));
        assertEquals(com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome.Disposition.APPLIED,
                accepted.disposition());
        assertEquals(2L, version(party));
    }

    @Test
    void successfulKeyedNoopStoresHistoricalResultForLaterReplay() {
        PartyEntity party = party();
        PartyNationalityEntity first = nationality(party, "EC", true, TODAY.minusDays(1), null);
        PartyNationalityEntity second = nationality(party, "CO", false, TODAY.minusDays(1), null);
        seed(party, List.of(first, second));
        var original = RootPartyFixtures.await(() -> mutations.setPrimary(keyed(party, first, "noop-key")));
        assertEquals(0L, version(party));
        RootPartyFixtures.await(() -> mutations.setPrimary(command(party, second)));
        assertEquals(1L, version(party));
        var replay = RootPartyFixtures.await(() -> mutations.setPrimary(keyed(party, first, "noop-key")));
        assertEquals(original.nationality(), replay.nationality());
        assertEquals(com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome.Disposition.REPLAYED,
                replay.disposition());
        assertFalse(read(party, first).isPrimary());
        assertEquals(1L, version(party));
    }

    private static PartyEntity party() {
        return new PartyEntity(UUID.randomUUID(), UUID.randomUUID(), PartyType.LEGAL_ENTITY,
                "Primary fixture", PartyRecordStatus.ARCHIVED, AuditInfo.initial(CREATED, "tester"), 0);
    }

    private static PartyNationalityEntity nationality(PartyEntity party, String country, boolean primary,
            LocalDate from, LocalDate until) {
        return new PartyNationalityEntity(new PartyNationality(new NationalityId(UUID.randomUUID()),
                new PartyId(party.id()), country, primary, new NationalityPeriod(from, until),
                AuditInfo.initial(CREATED, "tester")));
    }

    private static SetPrimaryNationalityCommand command(PartyEntity party, PartyNationalityEntity target) {
        return new SetPrimaryNationalityCommand(
                new RequestMetadata(new TenantId(party.tenantId()), "editor", UUID.randomUUID()),
                new PartyId(party.id()), new NationalityId(target.id()), TODAY, Optional.empty());
    }

    private static SetPrimaryNationalityCommand keyed(PartyEntity party, PartyNationalityEntity target,
            String key) {
        return new SetPrimaryNationalityCommand(
                new RequestMetadata(new TenantId(party.tenantId()), "editor", UUID.randomUUID()),
                new PartyId(party.id()), new NationalityId(target.id()), TODAY, Optional.of(key));
    }

    private void seed(PartyEntity party, List<PartyNationalityEntity> history) {
        RootPartyFixtures.await(() -> sessions.withTransaction((session, transaction) -> {
            Uni<Void> sequence = session.persist(party);
            for (PartyNationalityEntity item : history) {
                sequence = sequence.call(() -> session.persist(item));
            }
            return sequence.call(session::flush);
        }));
    }

    private com.alexastudillo.partyregistry.application.model.NationalityResult read(PartyEntity party,
            PartyNationalityEntity item) {
        return RootPartyFixtures.await(() -> reads.findById(new TenantId(party.tenantId()),
                new PartyId(party.id()), new NationalityId(item.id()))).orElseThrow();
    }

    private long version(PartyEntity party) {
        return RootPartyFixtures.await(() -> sessions.withSession(session -> session.createQuery(
                "select party.version from PartyEntity party where party.id = :id", Long.class)
                .setParameter("id", party.id()).getSingleResult()));
    }
}
