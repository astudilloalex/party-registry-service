package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionAudit;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierReason;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Verifies exact private withdrawal audit durability, qualified constraints and same-transaction rollback with events disabled. */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
@Timeout(120)
class PartyIdentifierAuditPersistenceTest {

    private static final Instant OCCURRED = LocalDate.of(2026, Month.OCTOBER, 4).atStartOfDay(ZoneOffset.UTC).toInstant().plusNanos(123456000);
    private static final String ACTOR = "original-audit-actor";
    private static final String SELECT_AUDIT = "SELECT action, resulting_version, actor, occurred_at, reason_code, reason_detail FROM party_identifier_action_audit WHERE tenant_id = :tenant AND identifier_id = :identifier AND resulting_version = :version";

    @Inject
    Mutiny.SessionFactory sessions;
    @Inject
    PartyIdentifierAuditPersistence persistence;

    @Test
    @RunOnVertxContext
    void storesExactReasonsAndAttributionAcrossFreshSessionsWithoutEvents(UniAsserter asserter) {
        Fixture fixture = fixture();
        asserter.execute(() -> owners(fixture));
        List<PartyIdentifierReason> reasons = List.of(PartyIdentifierReason.none(), new PartyIdentifierReason("", ""),
                new PartyIdentifierReason("Code\u0000\r\n", "Detail\u0000\u0085\ud800x\udfff"),
                new PartyIdentifierReason("\ud83d\ude00".repeat(64), "\ud83d\ude00".repeat(1000)));
        for (int index = 0; index < reasons.size(); index++) {
            PartyIdentifierActionAudit audit = audit(fixture, index + 1L, reasons.get(index));
            asserter.execute(() -> write(audit));
            asserter.assertThat(() -> read(audit), rows -> {
                assertEquals(1, rows.size());
                Object[] row = rows.getFirst();
                assertEquals(audit.operation().name(), row[0]);
                assertEquals(audit.resultingVersion().value(), ((Number) row[1]).longValue());
                assertEquals(ACTOR, row[2]);
                assertEquals(OCCURRED, ((OffsetDateTime) row[3]).toInstant());
                assertEquals(audit.reason().reasonCode(), PartyIdentifierReasonCodec.decode((byte[]) row[4], 64));
                assertEquals(audit.reason().reasonDetail(), PartyIdentifierReasonCodec.decode((byte[]) row[5], 1000));
            });
            assertFalse(audit.toString().contains("Detail"));
        }
        asserter.assertThat(() -> timed(sessions.withSession(session -> session.createNativeQuery(
                "SELECT count(*) FROM party_outbox_events WHERE aggregate_id = :identifier", Long.class)
                .setParameter("identifier", fixture.identifier().id()).getSingleResult())), count -> assertEquals(0L, count));
    }

    @Test
    @RunOnVertxContext
    void duplicateAcceptedActionIsRejectedWithoutDuplicatingHistory(UniAsserter asserter) {
        Fixture fixture = fixture();
        var audit = audit(fixture, 1, PartyIdentifierReason.none());
        asserter.execute(() -> owners(fixture));
        asserter.execute(() -> write(audit));
        asserter.assertFailedWith(() -> write(audit), failure -> assertConstraint(failure, "uq_party_identifier_audit_result"));
        asserter.assertThat(() -> read(audit), rows -> assertEquals(1, rows.size()));
    }

    @Test
    @RunOnVertxContext
    void ownerMismatchCannotInsertPrivateAudit(UniAsserter asserter) {
        Fixture fixture = fixture();
        var valid = audit(fixture, 1, PartyIdentifierReason.none());
        var wrong = new PartyIdentifierActionAudit(valid.requestMetadata(), new PartyId(UUID.randomUUID()), valid.identifierId(),
                valid.resultingVersion(), valid.operation(), valid.occurredAt(), valid.reason());
        asserter.execute(() -> owners(fixture));
        asserter.assertFailedWith(() -> write(wrong), failure -> assertConstraint(failure, "fk_party_identifier_audit_owner"));
        asserter.assertThat(() -> read(valid), rows -> assertTrue(rows.isEmpty()));
    }

    @Test
    @RunOnVertxContext
    void lateFailureLeavesNoAcceptedReasonAudit(UniAsserter asserter) {
        Fixture fixture = fixture();
        var audit = audit(fixture, 1, new PartyIdentifierReason("private-code", "private-detail"));
        asserter.execute(() -> owners(fixture));
        asserter.assertFailedWith(() -> timed(sessions.withTransaction((session, transaction) -> persistence.recordAudit(session, audit)
                .flatMap(ignored -> Uni.createFrom().failure(new IllegalStateException("synthetic-audit-failure"))))), IllegalStateException.class);
        asserter.assertThat(() -> read(audit), rows -> assertTrue(rows.isEmpty()));
    }

    private Uni<Void> owners(Fixture fixture) {
        return timed(sessions.withTransaction((session, transaction) -> session.persist(fixture.party())
                .call(() -> session.persist(fixture.identifier())).call(session::flush)));
    }

    private Uni<Void> write(PartyIdentifierActionAudit audit) {
        return timed(sessions.withTransaction((session, transaction) -> persistence.recordAudit(session, audit)));
    }

    private Uni<List<Object[]>> read(PartyIdentifierActionAudit audit) {
        return timed(sessions.withSession(session -> session.createNativeQuery(SELECT_AUDIT, Object[].class)
                .setParameter("tenant", audit.requestMetadata().tenantId().value()).setParameter("identifier", audit.identifierId().value())
                .setParameter("version", audit.resultingVersion().value()).getResultList()));
    }

    private static PartyIdentifierActionAudit audit(Fixture fixture, long version, PartyIdentifierReason reason) {
        var metadata = new RequestMetadata(new TenantId(fixture.party().tenantId()), ACTOR, UUID.randomUUID());
        return new PartyIdentifierActionAudit(metadata, new PartyId(fixture.party().id()), new PartyIdentifierId(fixture.identifier().id()),
                new PartyIdentifierVersion(version), version % 2 == 0 ? PartyIdentifierOperation.REJECT : PartyIdentifierOperation.REVOKE, OCCURRED, reason);
    }

    private static Fixture fixture() {
        AuditInfo audit = AuditInfo.initial(OCCURRED, ACTOR);
        var party = new PartyEntity(UUID.randomUUID(), UUID.randomUUID(), PartyType.NATURAL_PERSON, "Audit owner", PartyRecordStatus.DRAFT, audit, 0);
        party.attachNaturalPersonDetails(new NaturalPersonDetailsEntity(party.id(), new NaturalPersonDetails("Audit", "Owner", null, null, null, null), audit));
        var identifier = PartyIdentifierEntity.builder().id(UUID.randomUUID()).tenantId(party.tenantId()).partyId(party.id())
                .identifierSchemeId(IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID).issuerCode(null).encryptedValue("synthetic-audit-ciphertext")
                .encryptionKeyVersion((short) 1).normalizedValueHash(UUID.randomUUID().toString().replace("-", "").repeat(2))
                .maskedValue("***1234").normalizationVersion((short) 1).status(PartyIdentifierStatus.REVOKED).primary(false)
                .issuedOn(null).expiresOn(null).verifiedAt(OCCURRED).verifiedBy(ACTOR).createdAt(OCCURRED).createdBy(ACTOR)
                .updatedAt(OCCURRED).updatedBy(ACTOR).version(9).build();
        return new Fixture(party, identifier);
    }

    private static void assertConstraint(Throwable failure, String constraint) {
        Throwable cause = failure;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.contains(constraint)) { return; }
            cause = cause.getCause();
        }
        fail("Expected database audit constraint was not reported");
    }

    private static <T> Uni<T> timed(Uni<T> work) { return work.ifNoItem().after(Duration.ofSeconds(15)).fail(); }

    /** Groups one persisted qualified owner and identifier without exposing audit reason text. */
    private record Fixture(PartyEntity party, PartyIdentifierEntity identifier) { }
}
