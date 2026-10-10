package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.PartyIdentifierPrimaryPolicy;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies guarded identifier writes, protected history, exact increments and immediate-index-safe primary replacement. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(120)
class PartyIdentifierMutationPersistenceTest {

    private static final Instant CREATED = LocalDate.of(2026, Month.OCTOBER, 4).atStartOfDay(ZoneOffset.UTC).toInstant();
    private static final Instant CHANGED = CREATED.plusSeconds(1);
    private static final String ACTOR = "original-actor";

    @Inject
    Mutiny.SessionFactory sessions;
    @Inject
    PartyIdentifierMutationPersistence persistence;
    @Inject
    PartyIdentifierPersistenceMapper mapper;

    @Test
    @RunOnVertxContext
    void insertAndLifecycleWritesReloadExactlyOneVersionWithoutProtectionChanges(UniAsserter asserter) {
        Fixture fixture = fixture();
        var original = identifier(fixture, PartyIdentifierStatus.PENDING_VERIFICATION, false, 0, hash());
        var verified = original.verify(CHANGED, "verifier");
        var revoked = verified.revoke(CHANGED.plusSeconds(1), "withdrawer");
        asserter.execute(() -> seedOwner(fixture));
        asserter.assertThat(() -> timed(sessions.withTransaction((session, transaction) -> persistence.insertIdentifier(session, original))),
                result -> { assertEquals(0, result.version().value()); assertEquals(CREATED, result.createdAt()); });
        asserter.assertThat(() -> write(verified, original.version()), result -> { assertEquals(1, result.version().value()); assertEquals("verifier", result.verifiedBy()); });
        asserter.assertThat(() -> write(revoked, verified.version()), result -> { assertEquals(2, result.version().value()); assertEquals(PartyIdentifierStatus.REVOKED, result.status()); });
        asserter.assertThat(() -> read(original), stored -> {
            assertEquals(original.protectedValue(), stored.protectedValue());
            assertEquals(CREATED, stored.auditInfo().createdAt());
            assertEquals(ACTOR, stored.auditInfo().createdBy());
            assertEquals("withdrawer", stored.auditInfo().updatedBy());
            assertEquals(verified.verifiedAt(), stored.verifiedAt());
        });
        asserter.assertThat(() -> timed(sessions.withSession(session -> session.find(PartyEntity.class, fixture.party().id()))), party -> assertEquals(0, party.version()));
    }

    @Test
    @RunOnVertxContext
    void staleExpectedVersionLeavesTheCommittedIdentifierUnchanged(UniAsserter asserter) {
        Fixture fixture = fixture();
        var original = identifier(fixture, PartyIdentifierStatus.PENDING_VERIFICATION, false, 0, hash());
        var verified = original.verify(CHANGED, ACTOR);
        asserter.execute(() -> seed(fixture, original));
        asserter.execute(() -> write(verified, original.version()));
        asserter.assertFailedWith(() -> write(verified, original.version()), failure -> assertFailure(failure, ApplicationFailure.IdentifierVersionMismatch.class));
        asserter.assertThat(() -> read(original), stored -> { assertEquals(1, stored.version().value()); assertEquals(CHANGED, stored.auditInfo().updatedAt()); });
    }

    @Test
    @RunOnVertxContext
    void activeValueDuplicatesUseOnlyTheDeclaredUniquenessConflict(UniAsserter asserter) {
        Fixture fixture = fixture();
        String sameValue = hash();
        var original = identifier(fixture, PartyIdentifierStatus.PENDING_VERIFICATION, false, 0, sameValue);
        var duplicate = identifier(fixture, PartyIdentifierStatus.PENDING_VERIFICATION, false, 0, sameValue);
        asserter.execute(() -> seed(fixture, original));
        asserter.assertFailedWith(() -> timed(sessions.withTransaction((session, transaction) -> persistence.insertIdentifier(session, duplicate))),
                failure -> assertFailure(failure, ApplicationFailure.IdentifierUniquenessConflict.class));
        asserter.assertThat(() -> read(original), stored -> assertEquals(0, stored.version().value()));
    }

    @Test
    @RunOnVertxContext
    void demotionCompletesBeforePromotionUnderTheImmediatePrimaryIndex(UniAsserter asserter) {
        Fixture fixture = fixture();
        var former = identifier(fixture, PartyIdentifierStatus.VERIFIED, true, 7, hash());
        var target = identifier(fixture, PartyIdentifierStatus.VERIFIED, false, 9, hash());
        var plan = new PartyIdentifierPrimaryPolicy().planSelection(target, former, true, CHANGED, "selector");
        asserter.execute(() -> seed(fixture, former));
        asserter.execute(() -> timed(sessions.withTransaction((session, transaction) -> session.persist(mapper.toEntity(target)).call(session::flush))));
        asserter.assertThat(() -> timed(sessions.withTransaction((session, transaction) -> persistence.persistPrimaryPlan(session, plan))),
                result -> { assertEquals(target.identifierId(), result.identifierId()); assertTrue(result.isPrimary()); assertEquals(10, result.version().value()); });
        asserter.assertThat(() -> read(former), stored -> {
            assertFalse(stored.isPrimary()); assertEquals(8, stored.version().value());
            assertEquals(former.protectedValue(), stored.protectedValue()); assertEquals(former.verifiedAt(), stored.verifiedAt());
            assertEquals(CHANGED, stored.auditInfo().updatedAt());
        });
    }

    @Test
    @RunOnVertxContext
    void promotingWithoutDemotionTranslatesOnlyTheKnownPrimaryConstraint(UniAsserter asserter) {
        Fixture fixture = fixture();
        var former = identifier(fixture, PartyIdentifierStatus.VERIFIED, true, 7, hash());
        var target = identifier(fixture, PartyIdentifierStatus.VERIFIED, false, 9, hash());
        var selected = target.selectPrimary(CHANGED, ACTOR);
        asserter.execute(() -> seed(fixture, former));
        asserter.execute(() -> timed(sessions.withTransaction((session, transaction) -> session.persist(mapper.toEntity(target)).call(session::flush))));
        asserter.assertFailedWith(() -> write(selected, target.version()), failure -> assertFailure(failure, ApplicationFailure.PrimaryIdentifierConflict.class));
        asserter.assertThat(() -> read(target), stored -> { assertFalse(stored.isPrimary()); assertEquals(9, stored.version().value()); });
    }

    @Test
    @RunOnVertxContext
    void wrongOwnerAndProtectionChangesFailWithoutTouchingTheOriginal(UniAsserter asserter) {
        Fixture fixture = fixture();
        var original = identifier(fixture, PartyIdentifierStatus.PENDING_VERIFICATION, false, 0, hash());
        var verified = original.verify(CHANGED, ACTOR);
        var wrongOwner = forged(verified, new PartyId(UUID.randomUUID()), verified.protectedValue());
        var changedProtection = forged(verified, verified.partyId(), new ProtectedIdentifierValue(
                "different-ciphertext", 1, hash(), "***5678", new IdentifierRuleVersion(1)));
        asserter.execute(() -> seed(fixture, original));
        asserter.assertFailedWith(() -> write(wrongOwner, original.version()), IllegalStateException.class);
        asserter.assertFailedWith(() -> write(changedProtection, original.version()), IllegalStateException.class);
        asserter.assertThat(() -> read(original), stored -> {
            assertEquals(original.protectedValue(), stored.protectedValue());
            assertEquals(original.auditInfo(), stored.auditInfo());
            assertEquals(0, stored.version().value());
        });
    }

    @Test
    @RunOnVertxContext
    void insertingHistoricalVersionDoesNotCreateAnAcceptedRecord(UniAsserter asserter) {
        Fixture fixture = fixture();
        var historical = identifier(fixture, PartyIdentifierStatus.VERIFIED, false, 7, hash());
        asserter.execute(() -> seedOwner(fixture));
        asserter.assertFailedWith(() -> timed(sessions.withTransaction((session, transaction) -> persistence.insertIdentifier(session, historical))), IllegalStateException.class);
        asserter.assertThat(() -> timed(sessions.withSession(session -> session.find(PartyIdentifierEntity.class, historical.identifierId().value()))),
                org.junit.jupiter.api.Assertions::assertNull);
    }

    @Test
    @RunOnVertxContext
    void metadataWritesBindDatesAndExplicitNullsWithoutChangingVerification(UniAsserter asserter) {
        Fixture fixture = fixture();
        var original = identifier(fixture, PartyIdentifierStatus.VERIFIED, false, 7, hash());
        LocalDate issued = LocalDate.of(2026, Month.OCTOBER, 1);
        LocalDate expires = LocalDate.of(2026, Month.OCTOBER, 31);
        var corrected = original.correctMetadata(FieldUpdate.present("new_authority"), FieldUpdate.present(issued), FieldUpdate.present(expires), CHANGED, ACTOR);
        var cleared = corrected.correctMetadata(FieldUpdate.present(null), FieldUpdate.present(null), FieldUpdate.present(null), CHANGED.plusSeconds(1), ACTOR);
        asserter.execute(() -> seed(fixture, original));
        asserter.assertThat(() -> write(corrected, original.version()), result -> {
            assertEquals("NEW_AUTHORITY", result.issuerCode()); assertEquals(issued, result.issuedOn()); assertEquals(expires, result.expiresOn());
        });
        asserter.assertThat(() -> write(cleared, corrected.version()), result -> {
            assertNull(result.issuerCode()); assertNull(result.issuedOn()); assertNull(result.expiresOn());
            assertEquals(original.verifiedAt(), result.verifiedAt()); assertEquals(9, result.version().value());
        });
    }

    @Test
    @RunOnVertxContext
    void stalePromotionRollsBackItsAlreadyAppliedDemotion(UniAsserter asserter) {
        Fixture fixture = fixture();
        var former = identifier(fixture, PartyIdentifierStatus.VERIFIED, true, 7, hash());
        var target = identifier(fixture, PartyIdentifierStatus.VERIFIED, false, 9, hash());
        var plan = new PartyIdentifierPrimaryPolicy().planSelection(target, former, true, CHANGED.plusSeconds(1), ACTOR);
        var advanced = target.correctMetadata(FieldUpdate.present("new_authority"), FieldUpdate.absent(), FieldUpdate.absent(), CHANGED, ACTOR);
        asserter.execute(() -> seed(fixture, former));
        asserter.execute(() -> timed(sessions.withTransaction((session, transaction) -> session.persist(mapper.toEntity(advanced)).call(session::flush))));
        asserter.assertFailedWith(() -> timed(sessions.withTransaction((session, transaction) -> persistence.persistPrimaryPlan(session, plan))),
                failure -> assertFailure(failure, ApplicationFailure.IdentifierVersionMismatch.class));
        asserter.assertThat(() -> read(former), stored -> { assertTrue(stored.isPrimary()); assertEquals(7, stored.version().value()); assertEquals(former.auditInfo(), stored.auditInfo()); });
        asserter.assertThat(() -> read(target), stored -> { assertFalse(stored.isPrimary()); assertEquals(10, stored.version().value()); assertEquals("NEW_AUTHORITY", stored.issuerCode()); });
    }

    private static PartyIdentifier forged(PartyIdentifier source, PartyId party, ProtectedIdentifierValue protectedValue) {
        return PartyIdentifier.builder().identifierId(source.identifierId()).tenantId(source.tenantId()).partyId(party)
                .identifierSchemeId(source.identifierSchemeId()).protectedValue(protectedValue).issuerCode(source.issuerCode())
                .issuedOn(source.issuedOn()).expiresOn(source.expiresOn()).status(source.status()).primary(source.isPrimary())
                .verifiedAt(source.verifiedAt()).verifiedBy(source.verifiedBy()).version(source.version()).auditInfo(source.auditInfo()).build();
    }

    private Uni<PartyIdentifierResult> write(PartyIdentifier candidate, PartyIdentifierVersion expected) {
        return timed(sessions.withTransaction((session, transaction) -> persistence.updateIdentifier(session, candidate, expected)));
    }

    private Uni<PartyIdentifier> read(PartyIdentifier identifier) {
        return timed(sessions.withSession(session -> session.find(PartyIdentifierEntity.class, identifier.identifierId().value()).map(mapper::toDomain)));
    }

    private Uni<Void> seedOwner(Fixture fixture) {
        return timed(sessions.withTransaction((session, transaction) -> session.persist(fixture.party()).call(session::flush)));
    }

    private Uni<Void> seed(Fixture fixture, PartyIdentifier identifier) {
        return timed(sessions.withTransaction((session, transaction) -> session.persist(fixture.party()).call(() -> session.persist(mapper.toEntity(identifier))).call(session::flush)));
    }

    private static <T> Uni<T> timed(Uni<T> work) { return work.ifNoItem().after(Duration.ofSeconds(20)).fail(); }

    private static void assertFailure(Throwable failure, Class<? extends ApplicationFailure> type) {
        var application = assertInstanceOf(ApplicationException.class, failure);
        assertInstanceOf(type, application.failure());
    }

    private static String hash() { return UUID.randomUUID().toString().replace("-", "").repeat(2); }

    private static PartyIdentifier identifier(Fixture fixture, PartyIdentifierStatus status, boolean primary, long version, String hash) {
        boolean verified = status == PartyIdentifierStatus.VERIFIED;
        return PartyIdentifier.builder().identifierId(new PartyIdentifierId(UUID.randomUUID())).tenantId(new TenantId(fixture.party().tenantId()))
                .partyId(new PartyId(fixture.party().id())).identifierSchemeId(new IdentifierSchemeId(IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID))
                .protectedValue(new ProtectedIdentifierValue("synthetic-write-ciphertext", 1, hash, "***1234", new IdentifierRuleVersion(1)))
                .issuerCode(verified ? "legacy-authority" : "FIXTURE_AUTHORITY").issuedOn(null).expiresOn(null).primary(primary).status(status)
                .verifiedAt(verified ? CREATED : null).verifiedBy(verified ? ACTOR : null).version(new PartyIdentifierVersion(version))
                .auditInfo(AuditInfo.initial(CREATED, ACTOR)).build();
    }

    private static Fixture fixture() {
        var audit = AuditInfo.initial(CREATED, ACTOR);
        var party = new PartyEntity(UUID.randomUUID(), UUID.randomUUID(), PartyType.NATURAL_PERSON, "Write owner", PartyRecordStatus.DRAFT, audit, 0);
        party.attachNaturalPersonDetails(new NaturalPersonDetailsEntity(party.id(), new NaturalPersonDetails("Write", "Owner", null, null, null, null), audit));
        return new Fixture(party);
    }

    /** Carries an independently persisted owner for qualified identifier mutation checks. */
    private record Fixture(PartyEntity party) { }
}
