package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies qualified shared-owner and locked child restoration without changing versions or protected history. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(120)
class PartyIdentifierMutationReaderTest {

    @Inject
    Mutiny.SessionFactory sessions;
    @Inject
    PartyIdentifierMutationReader reader;
    @Inject
    PartyIdentifierMutationLocks locks;

    @Test
    @RunOnVertxContext
    void restoresHistoricalTargetAfterOwnedPartyAndScopeLocks(UniAsserter asserter) {
        for (PartyIdentifierStatus status : PartyIdentifierStatus.values()) {
            var fixture = fixture(status);
            var scope = fixture.scope();
            asserter.execute(() -> sessions.withTransaction((session, transaction) -> session.persist(fixture.party())
                    .call(() -> session.persist(fixture.identifier())).call(session::flush)));
            asserter.assertThat(() -> sessions.withTransaction((session, transaction) ->
                    reader.findOwnedParty(session, scope.tenantId(), scope.partyId())
                            .invoke(type -> assertEquals(PartyType.NATURAL_PERSON, type.orElseThrow()))
                            .chain(() -> reader.findIdentifierSchemeId(session, scope.tenantId(), scope.partyId(), fixture.identifier().id()))
                            .invoke(scheme -> assertEquals(scope.schemeId(), scheme.orElseThrow().value()))
                            .call(() -> locks.serializeScope(session, scope))
                            .chain(() -> reader.findForUpdate(session, scope, fixture.identifier().id()))), result -> {
                var restored = result.orElseThrow();
                assertEquals(status, restored.status());
                assertEquals(7, restored.version().value());
                assertEquals("synthetic-restored-ciphertext", restored.protectedValue().encryptedValue());
                assertEquals("legacy-authority", restored.issuerCode());
            });
            asserter.assertThat(() -> sessions.withSession(session -> session.find(PartyEntity.class, scope.partyId())), party -> assertEquals(0, party.version()));
        }
    }

    @Test
    @RunOnVertxContext
    void concealsWrongOwnersAndScopesOnEveryTargetLookup(UniAsserter asserter) {
        var fixture = fixture(PartyIdentifierStatus.VERIFIED);
        var scope = fixture.scope();
        asserter.execute(() -> sessions.withTransaction((session, transaction) -> session.persist(fixture.party())
                .call(() -> session.persist(fixture.identifier())).call(session::flush)));
        asserter.assertThat(() -> sessions.withTransaction((session, transaction) -> reader.findOwnedParty(session, UUID.randomUUID(), scope.partyId())),
                result -> assertTrue(result.isEmpty()));
        asserter.assertThat(() -> sessions.withSession(session -> reader.findIdentifierSchemeId(session, scope.tenantId(), UUID.randomUUID(), fixture.identifier().id())),
                result -> assertTrue(result.isEmpty()));
        var wrong = new PartyIdentifierMutationLocks.Scope(scope.tenantId(), UUID.randomUUID(), scope.schemeId());
        asserter.assertThat(() -> sessions.withTransaction((session, transaction) -> reader.findForUpdate(session, wrong, fixture.identifier().id())),
                result -> assertTrue(result.isEmpty()));
        var wrongScheme = new PartyIdentifierMutationLocks.Scope(scope.tenantId(), scope.partyId(), UUID.randomUUID());
        asserter.assertThat(() -> sessions.withTransaction((session, transaction) -> reader.findForUpdate(session, wrongScheme, fixture.identifier().id())),
                result -> assertTrue(result.isEmpty()));
    }

    @Test
    @RunOnVertxContext
    void resolvesCreationSchemeAndRetainsPastDateVerifiedPrimary(UniAsserter asserter) {
        var fixture = fixture(PartyIdentifierStatus.VERIFIED, true);
        var scope = fixture.scope();
        asserter.execute(() -> sessions.withTransaction((session, transaction) -> session.persist(fixture.party())
                .call(() -> session.persist(fixture.identifier())).call(session::flush)));
        asserter.assertThat(() -> sessions.withTransaction((session, transaction) ->
                reader.findOwnedParty(session, scope.tenantId(), scope.partyId())
                        .chain(() -> reader.findSchemeIdByCode(session, IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE))
                        .invoke(scheme -> assertEquals(scope.schemeId(), scheme.orElseThrow().value()))
                        .call(() -> locks.serializeScope(session, scope))
                        .chain(() -> reader.findSchemeForRegistration(session, scope.schemeId()))
                        .invoke(scheme -> assertEquals(scope.schemeId(), scheme.orElseThrow().id().value()))
                        .chain(() -> reader.findCurrentPrimary(session, scope))), result -> {
            var primary = result.orElseThrow();
            assertEquals(fixture.identifier().id(), primary.identifierId().value());
            assertTrue(primary.isPrimary());
            assertEquals(LocalDate.of(2026, Month.OCTOBER, 3), primary.expiresOn());
            assertEquals(7, primary.version().value());
        });
    }

    private static Fixture fixture(PartyIdentifierStatus status) {
        return fixture(status, false);
    }

    private static Fixture fixture(PartyIdentifierStatus status, boolean primary) {
        var instant = LocalDate.of(2026, Month.OCTOBER, 4).atStartOfDay(ZoneOffset.UTC).toInstant();
        var audit = AuditInfo.initial(instant, "original-owner");
        var party = new PartyEntity(UUID.randomUUID(), UUID.randomUUID(), PartyType.NATURAL_PERSON, "Locked owner", PartyRecordStatus.ARCHIVED, audit, 0);
        party.attachNaturalPersonDetails(new NaturalPersonDetailsEntity(party.id(), new NaturalPersonDetails("Locked", "Owner", null, null, null, null), audit));
        boolean verified = status == PartyIdentifierStatus.VERIFIED || status == PartyIdentifierStatus.REVOKED;
        var identifier = PartyIdentifierEntity.builder().id(UUID.randomUUID()).tenantId(party.tenantId()).partyId(party.id())
                .identifierSchemeId(IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID).issuerCode("legacy-authority").encryptedValue("synthetic-restored-ciphertext")
                .encryptionKeyVersion((short) 1).normalizedValueHash(UUID.randomUUID().toString().replace("-", "").repeat(2)).maskedValue("***1234")
                .normalizationVersion((short) 1).status(status).primary(primary).issuedOn(null).expiresOn(LocalDate.of(2026, Month.OCTOBER, 3))
                .verifiedAt(verified ? instant : null).verifiedBy(verified ? "original-owner" : null).createdAt(instant).createdBy("original-owner")
                .updatedAt(instant).updatedBy("original-owner").version(7).build();
        return new Fixture(party, identifier);
    }

    /** Groups the qualified persisted owner and protected child used by restoration checks. */
    private record Fixture(PartyEntity party, PartyIdentifierEntity identifier) {
        PartyIdentifierMutationLocks.Scope scope() {
            return new PartyIdentifierMutationLocks.Scope(party.tenantId(), party.id(), identifier.identifierSchemeId());
        }
    }
}
