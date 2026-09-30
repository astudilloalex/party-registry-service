package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.PatchNationalityCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.NationalityMutationPort;
import com.alexastudillo.partyregistry.application.port.NationalityReadPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies atomic date-only PATCH, unchanged accepted audit updates, and
 * rollback of conflicting intervals.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(60)
class HibernateReactiveNationalityPatchTest {

        private static final Instant AT = Instant.parse("2026-01-01T10:00:00Z");
        private static final LocalDate JANUARY = LocalDate.of(2026, Month.JANUARY, 1);
        private static final LocalDate JANUARY_END = LocalDate.of(2026, Month.JANUARY, 31);
        private static final LocalDate FEBRUARY = LocalDate.of(2026, Month.FEBRUARY, 1);

        @Inject
        NationalityMutationPort mutations;

        @Inject
        NationalityReadPort reads;

        @Inject
        Mutiny.SessionFactory sessions;

        @Test
        void explicitNullClearsTheEndAndUnchangedAcceptedPatchAdvancesAuditAndRootVersion() {
                PartyEntity party = party();
                PartyNationalityEntity original = nationality(party, "EC", false, JANUARY, JANUARY_END);
                seed(party, List.of(original));
                var before = read(party, original);
                NationalityMutationOutcome cleared = RootPartyFixtures
                                .await(() -> mutations.patch(command(party, original,
                                                FieldUpdate.absent(), FieldUpdate.present(null))));
                assertEquals(before.nationalityId(), cleared.nationality().nationalityId());
                assertEquals(before.partyId(), cleared.nationality().partyId());
                assertEquals(before.countryCode(), cleared.nationality().countryCode());
                assertEquals(before.isPrimary(), cleared.nationality().isPrimary());
                assertEquals(JANUARY, cleared.nationality().validFrom());
                assertNull(cleared.nationality().validUntil());
                assertEquals(before.createdAt(), cleared.nationality().createdAt());
                assertTrue(cleared.nationality().updatedAt().isAfter(before.updatedAt()));
                assertEquals(1L, version(party));
                var repeated = RootPartyFixtures.await(() -> mutations.patch(command(party, original,
                                FieldUpdate.absent(), FieldUpdate.present(null))));
                assertTrue(repeated.nationality().updatedAt().isAfter(cleared.nationality().updatedAt()));
                assertEquals(2L, version(party));
        }

        @Test
        void invertedCountryAndPrimaryConflictsLeaveBothRowsAndAuditUntouched() {
                PartyEntity party = party();
                PartyNationalityEntity january = nationality(party, "EC", true, JANUARY, JANUARY_END);
                PartyNationalityEntity february = nationality(party, "EC", false, FEBRUARY, FEBRUARY.plusDays(27));
                PartyNationalityEntity otherPrimary = nationality(party, "CO", true, FEBRUARY, FEBRUARY.plusDays(27));
                seed(party, List.of(january, february, otherPrimary));
                var initialJanuary = read(party, january);
                var initialPrimary = read(party, otherPrimary);

                assertFailure(command(party, january, FieldUpdate.absent(), FieldUpdate.present(JANUARY.minusDays(1))),
                                ApplicationFailure.NationalityValidityInvalid.class);
                assertFailure(command(party, january, FieldUpdate.absent(), FieldUpdate.present(FEBRUARY)),
                                ApplicationFailure.NationalityValidityConflict.class);
                assertFailure(command(party, otherPrimary, FieldUpdate.present(JANUARY_END), FieldUpdate.absent()),
                                ApplicationFailure.PrimaryNationalityConflict.class);
                assertEquals(initialJanuary, read(party, january));
                assertEquals(initialPrimary, read(party, otherPrimary));
                assertEquals(0L, version(party));

                PartyEntity another = party();
                seed(another, List.of());
                assertFailure(command(another, january, FieldUpdate.absent(), FieldUpdate.present(null)),
                                ApplicationFailure.NationalityNotFound.class);
        }

        private void assertFailure(PatchNationalityCommand command, Class<?> type) {
                ApplicationException failed = org.junit.jupiter.api.Assertions.assertThrows(ApplicationException.class,
                                () -> RootPartyFixtures.await(() -> mutations.patch(command)));
                assertInstanceOf(type, failed.failure());
        }

        private static PartyEntity party() {
                return new PartyEntity(UUID.randomUUID(), UUID.randomUUID(), PartyType.LEGAL_ENTITY,
                                "Patch fixture", PartyRecordStatus.ARCHIVED, AuditInfo.initial(AT, "tester"), 0);
        }

        private static PartyNationalityEntity nationality(PartyEntity party, String country, boolean primary,
                        LocalDate from, LocalDate until) {
                return new PartyNationalityEntity(new PartyNationality(new NationalityId(UUID.randomUUID()),
                                new PartyId(party.id()), country, primary, new NationalityPeriod(from, until),
                                AuditInfo.initial(AT, "tester")));
        }

        private static PatchNationalityCommand command(PartyEntity party, PartyNationalityEntity item,
                        FieldUpdate<LocalDate> from, FieldUpdate<LocalDate> until) {
                return new PatchNationalityCommand(
                                new RequestMetadata(new TenantId(party.tenantId()), "editor", UUID.randomUUID()),
                                new PartyId(party.id()), new NationalityId(item.id()), from, until);
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
