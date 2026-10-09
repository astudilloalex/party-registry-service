package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies same-session identifier completion persistence, fresh-session durability and transaction rollback. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(120)
class PartyIdentifierReplayPersistenceTest {

    private static final Instant CREATED = LocalDate.of(2026, Month.OCTOBER, 4).atStartOfDay(ZoneOffset.UTC).toInstant().plusNanos(123456000);

    @Inject
    Mutiny.SessionFactory sessions;
    @Inject
    PartyIdentifierReplayPersistence replay;

    @Test
    @RunOnVertxContext
    void completionCommitsWithItsIdentifierAndSurvivesFreshSessionsAndExactKeyScopes(UniAsserter asserter) {
        Fixture fixture = fixture();
        var identity = identity(fixture, " exact-key ");
        var completed = completed(fixture);
        asserter.execute(() -> timed(sessions.withTransaction((session, transaction) ->
                session.persist(fixture.party()).call(() -> session.persist(fixture.identifier()))
                        .call(() -> replay.recordCompletion(session, identity, completed)).call(session::flush))));
        asserter.assertThat(() -> timed(sessions.withSession(session -> replay.findCompleted(session, identity))),
                result -> assertEquals(completed, result.orElseThrow()));
        asserter.assertThat(() -> timed(sessions.withSession(session -> replay.findCompleted(session, identity(fixture, "exact-key")))),
                result -> assertTrue(result.isEmpty()));
        var otherTenant = new PartyIdentifierIdempotencyRecordId(UUID.randomUUID(), PartyIdentifierOperation.CREATE.namespace(), " exact-key ");
        var otherAction = new PartyIdentifierIdempotencyRecordId(fixture.party().tenantId(), PartyIdentifierOperation.VERIFY.namespace(), " exact-key ");
        asserter.assertThat(() -> timed(sessions.withSession(session -> replay.findCompleted(session, otherTenant))), result -> assertTrue(result.isEmpty()));
        asserter.assertThat(() -> timed(sessions.withSession(session -> replay.findCompleted(session, otherAction))), result -> assertTrue(result.isEmpty()));
    }

    @Test
    @RunOnVertxContext
    void failureAfterCompletionFlushRollsBackTheWholeAcceptedOutcome(UniAsserter asserter) {
        Fixture fixture = fixture();
        var identity = identity(fixture, "rollback-key");
        var completed = completed(fixture);
        asserter.assertFailedWith(() -> timed(sessions.withTransaction((session, transaction) ->
                session.persist(fixture.party()).call(() -> session.persist(fixture.identifier()))
                        .call(() -> replay.recordCompletion(session, identity, completed)).call(session::flush)
                        .flatMap(ignored -> Uni.createFrom().failure(new IllegalStateException("synthetic-late-failure"))))), IllegalStateException.class);
        asserter.assertThat(() -> timed(sessions.withSession(session -> replay.findCompleted(session, identity))), result -> assertTrue(result.isEmpty()));
        asserter.assertThat(() -> timed(sessions.withSession(session -> session.find(PartyIdentifierEntity.class, fixture.identifier().id()))),
                Assertions::assertNull);
    }

    private static Fixture fixture() {
        AuditInfo audit = AuditInfo.initial(CREATED, "original-actor");
        var party = new PartyEntity(UUID.randomUUID(), UUID.randomUUID(), PartyType.NATURAL_PERSON, "Replay owner", PartyRecordStatus.DRAFT, audit, 0);
        party.attachNaturalPersonDetails(new NaturalPersonDetailsEntity(party.id(), new NaturalPersonDetails("Replay", "Owner", null, null, null, null), audit));
        var identifier = PartyIdentifierEntity.builder().id(UUID.randomUUID()).tenantId(party.tenantId()).partyId(party.id())
                .identifierSchemeId(IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID).issuerCode(null).encryptedValue("synthetic-completion-ciphertext")
                .encryptionKeyVersion((short) 1).normalizedValueHash(UUID.randomUUID().toString().replace("-", "").repeat(2))
                .maskedValue("***1234").normalizationVersion((short) 1).status(PartyIdentifierStatus.PENDING_VERIFICATION).primary(false)
                .issuedOn(null).expiresOn(null).verifiedAt(null).verifiedBy(null).createdAt(CREATED).createdBy("original-actor")
                .updatedAt(CREATED).updatedBy("original-actor").version(0).build();
        return new Fixture(party, identifier);
    }

    private static CompletedPartyIdentifierMutation completed(Fixture fixture) {
        var identifier = fixture.identifier();
        var result = new PartyIdentifierResult(new PartyIdentifierId(identifier.id()), new PartyId(identifier.partyId()),
                new IdentifierSchemeId(identifier.identifierSchemeId()), IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE,
                identifier.maskedValue(), identifier.status(), identifier.isPrimary(), null, null, null, null, null,
                new PartyIdentifierVersion(0), CREATED, CREATED);
        return new CompletedPartyIdentifierMutation(new TenantId(identifier.tenantId()), PartyIdentifierOperation.CREATE,
                "a".repeat(64), result, null, "original-actor", CREATED);
    }

    private static PartyIdentifierIdempotencyRecordId identity(Fixture fixture, String key) {
        return new PartyIdentifierIdempotencyRecordId(fixture.party().tenantId(), PartyIdentifierOperation.CREATE.namespace(), key);
    }

    private static <T> Uni<T> timed(Uni<T> work) {
        return work.ifNoItem().after(Duration.ofSeconds(15)).fail();
    }

    /** Keeps one synthetic independently persisted owner and identifier for transaction-consistency checks. */
    private record Fixture(PartyEntity party, PartyIdentifierEntity identifier) { }
}
