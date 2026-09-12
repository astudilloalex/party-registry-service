package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.command.RegisterLegalEntityCommand;
import com.alexastudillo.partyregistry.application.command.RegisterNaturalPersonCommand;
import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.LegalEntityResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationResult;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies version-one and version-two idempotency rows coexist in PostgreSQL.
 */
@QuarkusTest
class ReactiveIdempotencySnapshotPersistenceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-04T12:30:00.123456Z");
    private static final LocalDate EVALUATED_ON = LocalDate.parse("2026-09-04");
    private static final String LEGACY_HASH = "a".repeat(64);
    private static final String REGISTRATION_FINGERPRINT = "b".repeat(64);

    @Inject
    PartyRegistrationIdempotencyPersistence registrationIdempotency;

    @Inject
    IdempotencyResultSnapshotCodec snapshotCodec;

    @Inject
    NaturalPersonPersistenceMapper naturalPersonMapper;

    @Inject
    LegalEntityPersistenceMapper legalEntityMapper;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    @RunOnVertxContext
    void storesAndReadsVersionTwoAlongsideExistenceOnlyLegacyDetection(UniAsserter asserter) {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        String legacyKey = "legacy-" + UUID.randomUUID();
        String registrationKey = "registration-" + UUID.randomUUID();
        NaturalPerson legacyParty = legacyNaturalPerson(tenantId);
        NaturalPersonResult legacyResult = NaturalPersonResult.fromAggregate(legacyParty);
        ApiIdempotencyRecordEntity legacyRecord = new ApiIdempotencyRecordEntity(
                new ApiIdempotencyRecordId(
                        tenantId.value(),
                        RegisterNaturalPersonCommand.LEGACY_OPERATION,
                        legacyKey),
                LEGACY_HASH,
                legacyParty.partyId().value(),
                snapshotCodec.encodeLegacyNaturalPerson(legacyResult),
                legacyParty.auditInfo().createdAt(),
                legacyParty.auditInfo().createdBy());
        LegalEntity legalEntity = legalEntity(tenantId);
        PartyRegistrationResult registrationResult = registrationResult(legalEntity);
        ApiIdempotencyRecordEntity registrationRecord = registrationIdempotency.newCompletedRecord(
                RegisterLegalEntityCommand.OPERATION,
                registrationKey,
                REGISTRATION_FINGERPRINT,
                registrationResult);

        asserter.execute(() -> withTimeout(persistPartyAndRecord(
                naturalPersonMapper.toEntity(legacyParty),
                legacyRecord)));
        asserter.execute(() -> withTimeout(persistPartyAndRecord(
                legalEntityMapper.toEntity(legalEntity),
                registrationRecord)));
        asserter.assertThat(
                () -> withTimeout(registrationIdempotency.findCompleted(
                        tenantId,
                        RegisterLegalEntityCommand.OPERATION,
                        registrationKey,
                        REGISTRATION_FINGERPRINT)),
                completed -> assertEquals(registrationResult, completed.orElseThrow()));
        asserter.assertThat(
                () -> withTimeout(registrationIdempotency.hasCompletedKey(
                        tenantId,
                        RegisterNaturalPersonCommand.LEGACY_OPERATION,
                        legacyKey)),
                completed -> assertTrue(completed));
        asserter.assertThat(
                () -> withTimeout(loadSnapshot(
                        tenantId,
                        RegisterNaturalPersonCommand.LEGACY_OPERATION,
                        legacyKey)),
                snapshot -> {
                    assertEquals(NaturalPersonResultSnapshot.CURRENT_SCHEMA_VERSION,
                            snapshot.schemaVersion());
                    assertEquals(legacyResult, snapshotCodec.decodeLegacyNaturalPerson(snapshot));
                });
        asserter.assertThat(
                () -> withTimeout(loadSnapshot(
                        tenantId,
                        RegisterLegalEntityCommand.OPERATION,
                        registrationKey)),
                snapshot -> {
                    assertEquals(IdempotencyResultSnapshotCodec.REGISTRATION_SCHEMA_VERSION,
                            snapshot.schemaVersion());
                    assertEquals(IdempotencyResultSnapshotCodec.REGISTRATION_SCHEMA_VERSION,
                            snapshot.payload().path("schemaVersion").intValue());
                });
        asserter.assertFailedWith(
                () -> withTimeout(registrationIdempotency.findCompleted(
                        tenantId,
                        RegisterNaturalPersonCommand.LEGACY_OPERATION,
                        legacyKey,
                        LEGACY_HASH)),
                failure -> {
                    ApplicationException exception = assertInstanceOf(ApplicationException.class, failure);
                    assertInstanceOf(ApplicationFailure.PersistenceFailure.class, exception.failure());
                    assertEquals("Persistence operation failed", exception.getMessage());
                });
    }

    private Uni<Void> persistPartyAndRecord(
            PartyEntity party,
            ApiIdempotencyRecordEntity idempotencyRecord) {
        return sessionFactory.withTransaction((session, transaction) -> session.persist(party)
                .call(session::flush)
                .call(() -> session.persist(idempotencyRecord))
                .call(session::flush));
    }

    private Uni<IdempotencyResultSnapshot> loadSnapshot(
            TenantId tenantId,
            String operation,
            String idempotencyKey) {
        return sessionFactory.withSession(session -> session.find(
                ApiIdempotencyRecordEntity.class,
                new ApiIdempotencyRecordId(tenantId.value(), operation, idempotencyKey))
                .map(ApiIdempotencyRecordEntity::resultSnapshot));
    }

    private static NaturalPerson legacyNaturalPerson(TenantId tenantId) {
        return NaturalPerson.create(
                new PartyId(UUID.randomUUID()),
                tenantId,
                null,
                new NaturalPersonDetails(
                        "Legacy",
                        "Natural Person",
                        null,
                        null,
                        null,
                        null),
                EVALUATED_ON,
                OCCURRED_AT,
                "legacy-creator");
    }

    private static LegalEntity legalEntity(TenantId tenantId) {
        return LegalEntity.create(
                new PartyId(UUID.randomUUID()),
                tenantId,
                null,
                new LegalEntityDetails(
                        "Version Two Legal Entity",
                        null,
                        null,
                        "GB",
                        null,
                        null),
                EVALUATED_ON,
                OCCURRED_AT.plusSeconds(1),
                "registration-creator");
    }

    private static PartyRegistrationResult registrationResult(LegalEntity legalEntity) {
        LegalEntityResult party = LegalEntityResult.fromAggregate(legalEntity);
        return new PartyRegistrationResult(
                party,
                new PartyIdentifierResult(
                        new PartyIdentifierId(UUID.randomUUID()),
                        legalEntity.partyId(),
                        new IdentifierSchemeId(UUID.randomUUID()),
                        "GB-LEGAL-ID",
                        "********4312",
                        PartyIdentifierStatus.PENDING_VERIFICATION,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        PartyIdentifierVersion.initial(),
                        OCCURRED_AT.plusSeconds(1),
                        OCCURRED_AT.plusSeconds(1)),
                PartyRegistrationOutcome.CREATED);
    }

    private static <T> Uni<T> withTimeout(Uni<T> operation) {
        return operation.ifNoItem().after(TIMEOUT).fail();
    }
}
