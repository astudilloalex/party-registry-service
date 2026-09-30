package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.CreateNationalityCommand;
import com.alexastudillo.partyregistry.application.command.SetPrimaryNationalityCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.NationalityPeriod;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import com.alexastudillo.partyregistry.support.RootPartyFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verifies independent versioned snapshots, effective fingerprints, and
 * tenant/action-scoped locking.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(60)
class NationalityIdempotencyPersistenceTest {

    private static final Instant CREATED = Instant.parse("2026-09-23T10:00:00Z");

    @Inject
    Mutiny.SessionFactory sessions;

    @Inject
    NationalityIdempotencyPersistence replay;

    @Test
    void storesHistoricalCreateAndPrimaryResultsInDifferentKeyNamespaces() {
        PartyEntity party = seed();
        RequestMetadata first = metadata(party, "first");
        NationalityId nationalityId = new NationalityId(UUID.randomUUID());
        CreateNationalityCommand create = new CreateNationalityCommand(first, new PartyId(party.id()), "EC",
                false,
                new NationalityPeriod(null, null), "same-key");
        NationalityResult created = new NationalityResult(nationalityId, create.partyId(), "EC", false,
                null, null, CREATED, CREATED);
        SetPrimaryNationalityCommand primary = new SetPrimaryNationalityCommand(first, create.partyId(),
                nationalityId,
                LocalDate.of(2026, Month.SEPTEMBER, 23), Optional.of("same-key"));
        NationalityResult designated = new NationalityResult(nationalityId, create.partyId(), "EC", true,
                null, null, CREATED, CREATED.plusSeconds(60));

        RootPartyFixtures.await(() -> sessions.withTransaction((session, transaction) -> replay
                .serialize(session, first.tenantId(), NationalityOperation.CREATE,
                        create.idempotencyKey())
                .call(() -> replay.recordCreate(session, create, created, CREATED.plusSeconds(1)))));
        RootPartyFixtures.await(() -> sessions.withTransaction((session, transaction) -> replay
                .serialize(session, first.tenantId(), NationalityOperation.SET_PRIMARY, "same-key")
                .call(() -> replay.recordPrimary(session, primary, "same-key", designated,
                        CREATED.plusSeconds(61)))));

        RequestMetadata otherAttempt = metadata(party, "other");
        CreateNationalityCommand equivalent = new CreateNationalityCommand(otherAttempt, create.partyId(), "EC",
                false,
                new NationalityPeriod(null, null), "same-key");
        SetPrimaryNationalityCommand later = new SetPrimaryNationalityCommand(otherAttempt, create.partyId(),
                nationalityId,
                LocalDate.of(2030, Month.JANUARY, 1), Optional.of("same-key"));
        assertEquals(NationalityRequestFingerprint.create(create),
                NationalityRequestFingerprint.create(equivalent));
        assertEquals(NationalityRequestFingerprint.setPrimary(primary),
                NationalityRequestFingerprint.setPrimary(later));
        assertEquals(created,
                RootPartyFixtures.await(() -> sessions
                        .withTransaction((session, transaction) -> replay
                                .serialize(session, otherAttempt.tenantId(),
                                        NationalityOperation.CREATE, "same-key")
                                .chain(() -> replay.findCreate(session, equivalent)))
                        .map(Optional::orElseThrow)));
        assertEquals(designated, RootPartyFixtures.await(() -> sessions
                .withTransaction((session, transaction) -> replay
                        .serialize(session, otherAttempt.tenantId(),
                                NationalityOperation.SET_PRIMARY, "same-key")
                        .chain(() -> replay.findPrimary(session, later, "same-key")))
                .map(Optional::orElseThrow)));
        assertEquals((short) 4, version(party, NationalityOperation.CREATE));
        assertEquals((short) 5, version(party, NationalityOperation.SET_PRIMARY));

        CreateNationalityCommand changed = new CreateNationalityCommand(otherAttempt, create.partyId(), "CO",
                false,
                new NationalityPeriod(null, null), "same-key");
        ApplicationException conflict = org.junit.jupiter.api.Assertions.assertThrows(
                ApplicationException.class,
                () -> RootPartyFixtures
                        .await(() -> sessions.withTransaction((session, transaction) -> replay
                                .serialize(session, otherAttempt.tenantId(),
                                        NationalityOperation.CREATE, "same-key")
                                .chain(() -> replay.findCreate(session, changed)))));
        assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, conflict.failure());
        CreateNationalityCommand otherParty = new CreateNationalityCommand(otherAttempt,
                new PartyId(UUID.randomUUID()),
                "EC", false, new NationalityPeriod(null, null), "same-key");
        ApplicationException crossParty = org.junit.jupiter.api.Assertions.assertThrows(
                ApplicationException.class,
                () -> RootPartyFixtures
                        .await(() -> sessions.withTransaction((session, transaction) -> replay
                                .serialize(session, otherAttempt.tenantId(),
                                        NationalityOperation.CREATE, "same-key")
                                .chain(() -> replay.findCreate(session, otherParty)))));
        assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, crossParty.failure());
        SetPrimaryNationalityCommand otherTarget = new SetPrimaryNationalityCommand(otherAttempt,
                create.partyId(),
                new NationalityId(UUID.randomUUID()), later.asOfDate(), Optional.of("same-key"));
        ApplicationException crossTarget = org.junit.jupiter.api.Assertions.assertThrows(
                ApplicationException.class,
                () -> RootPartyFixtures.await(() -> sessions.withTransaction((session,
                        transaction) -> replay.serialize(session, otherAttempt.tenantId(),
                                NationalityOperation.SET_PRIMARY, "same-key")
                                .chain(() -> replay.findPrimary(session, otherTarget,
                                        "same-key")))));
        assertInstanceOf(ApplicationFailure.IdempotencyKeyConflict.class, crossTarget.failure());
        assertNotEquals(NationalityIdempotencyPersistence.lockId(NationalityIdempotencyPersistence.id(
                first.tenantId(), NationalityOperation.CREATE, "same-key")),
                NationalityIdempotencyPersistence.lockId(NationalityIdempotencyPersistence.id(
                        first.tenantId(), NationalityOperation.SET_PRIMARY, "same-key")));
    }

    private PartyEntity seed() {
        PartyEntity party = new PartyEntity(UUID.randomUUID(), UUID.randomUUID(), PartyType.LEGAL_ENTITY,
                "Replay fixture", PartyRecordStatus.DRAFT, AuditInfo.initial(CREATED, "creator"), 0);
        RootPartyFixtures.await(
                () -> sessions.withTransaction((session, transaction) -> session.persist(party)));
        return party;
    }

    private short version(PartyEntity party, NationalityOperation operation) {
        return RootPartyFixtures.await(() -> sessions.withSession(session -> session.find(
                ApiIdempotencyRecordEntity.class, NationalityIdempotencyPersistence.id(
                        new TenantId(party.tenantId()), operation, "same-key"))
                .map(ApiIdempotencyRecordEntity::resultSnapshotSchemaVersion)));
    }

    private static RequestMetadata metadata(PartyEntity party, String actor) {
        return new RequestMetadata(new TenantId(party.tenantId()), actor, UUID.randomUUID());
    }
}
