package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyActivationCandidate;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.LegalEntityRepository;
import com.alexastudillo.partyregistry.application.port.PartyActivationPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.inject.Vetoed;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies legal root/detail atomicity using bounded Vert.x-context database
 * calls and independent race contexts.
 */
@QuarkusTest
@Timeout(60)
class HibernateReactiveLegalEntityRepositoryTest {

    private static final Duration WAIT = Duration.ofSeconds(15);
    private static final Instant CREATED = Instant.parse("2026-09-01T10:00:00.123456Z");
    private static final Instant UPDATED = Instant.parse("2026-09-13T10:00:00.123456Z");
    private static final LocalDate TODAY = LocalDate.of(2026, Month.SEPTEMBER, 13);

    @Inject
    LegalEntityRepository repository;

    @Inject
    LegalEntityPersistenceMapper mapper;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Inject
    PartyActivationPort activationPort;

    @Test
    void readsHistoricalDetailsAndConcealsAbsentOtherTenantAndWrongType() {
        LegalEntity original = original(PartyVersion.initial());
        await(() -> persist(original));
        assertAggregate(original, load(original));
        assertTrue(await(() -> repository.findByTenantAndId(new TenantId(UUID.randomUUID()), original.partyId()))
                .isEmpty());
        assertTrue(await(() -> repository.findByTenantAndId(original.tenantId(), new PartyId(UUID.randomUUID())))
                .isEmpty());
        PartyId naturalId = new PartyId(UUID.randomUUID());
        await(() -> sessionFactory.withTransaction((session, transaction) -> session.persist(new PartyEntity(
                naturalId.value(), original.tenantId().value(), PartyType.NATURAL_PERSON, "Other Type",
                PartyRecordStatus.DRAFT, original.auditInfo(), 0))));
        assertTrue(await(() -> repository.findByTenantAndId(original.tenantId(), naturalId)).isEmpty());
        LegalEntity wrongTypeCandidate = LegalEntity.restore(naturalId, original.tenantId(), original.displayName(),
                original.recordStatus(), original.version(), original.auditInfo(), original.details());
        assertInstanceOf(ApplicationFailure.LegalEntityNotFound.class,
                failure(() -> repository.update(wrongTypeCandidate, PartyVersion.initial())));
        LegalEntity otherTenantCandidate = LegalEntity.restore(original.partyId(), new TenantId(UUID.randomUUID()),
                original.displayName(), original.recordStatus(), original.version(), original.auditInfo(),
                original.details());
        assertInstanceOf(ApplicationFailure.LegalEntityNotFound.class,
                failure(() -> repository.update(otherTenantCandidate, PartyVersion.initial())));
        assertAggregate(original, load(original));
    }

    @Test
    void updatesBothAuditRowsAndVersionWithoutTouchingIdentifiers() {
        LegalEntity original = original(PartyVersion.initial());
        await(() -> persist(original));
        await(() -> insertVerifiedIdentifier(original));
        Object[] identifierBefore = await(() -> identifierState(original));
        LegalEntity candidate = replacement(original, " New Legal ", " Brand ", " sa ");
        LegalEntity result = await(() -> repository.update(candidate, PartyVersion.initial()));
        assertEquals(new PartyVersion(1), result.version());
        assertEquals("NEW LEGAL", result.displayName());
        assertEquals("NEW LEGAL", result.details().legalName());
        assertEquals("BRAND", result.details().tradeName());
        assertEquals("SA", result.details().legalFormCode());
        assertEquals(original.recordStatus(), result.recordStatus());
        assertEquals(new AuditInfo(CREATED, "creator", UPDATED, "editor"), result.auditInfo());
        assertAggregate(result, load(original));
        Object[] detailsAudit = await(() -> sessionFactory.withSession(session -> session.createQuery("""
                select details.createdAt, details.createdBy, details.updatedAt, details.updatedBy
                from LegalEntityDetailsEntity details where details.partyId = :partyId
                """, Object[].class).setParameter("partyId", original.partyId().value()).getSingleResult()));
        assertArrayEquals(new Object[] { CREATED, "creator", UPDATED, "editor" }, detailsAudit);
        assertArrayEquals(identifierBefore, await(() -> identifierState(original)));
        ApplicationFailure.ExpectedVersionMismatch mismatch = assertInstanceOf(
                ApplicationFailure.ExpectedVersionMismatch.class,
                failure(() -> repository.update(candidate, PartyVersion.initial())));
        assertEquals(PartyVersion.initial(), mismatch.expectedVersion());
        assertEquals(new PartyVersion(1), mismatch.currentVersion());
    }

    @Test
    void identicalValuesAndAuditStillIncrementTheRootExactlyOnce() {
        LegalEntity original = original(PartyVersion.initial());
        await(() -> persist(original));
        LegalEntity result = await(() -> repository.update(original, PartyVersion.initial()));
        assertEquals(new PartyVersion(1), result.version());
        assertEquals(original.details(), result.details());
        assertEquals(original.auditInfo(), result.auditInfo());
        assertEquals(original.displayName(), result.displayName());
        assertAggregate(result, load(original));
    }

    @Test
    void detailWriteFailureRollsBackTheAlreadyUpdatedRoot() {
        LegalEntity original = original(PartyVersion.initial());
        await(() -> persist(original));
        // PostgreSQL text rejects NUL after the root mutation; no test-only schema
        // change is needed.
        LegalEntity candidate = replacement(original, "Changed", "Changed", "BAD\u0000CODE");
        assertInstanceOf(ApplicationFailure.PersistenceFailure.class,
                failure(() -> repository.update(candidate, PartyVersion.initial())));
        assertAggregate(original, load(original));
    }

    @Test
    void reloadFailureRollsBackBothMutations() {
        LegalEntity original = original(PartyVersion.initial());
        await(() -> persist(original));
        var failingRepository = new HibernateReactiveLegalEntityRepository(sessionFactory, new FailingReloadMapper());
        LegalEntity candidate = replacement(original, "Changed", "Changed", "SA");
        assertInstanceOf(ApplicationFailure.PersistenceFailure.class,
                failure(() -> failingRepository.update(candidate, PartyVersion.initial())));
        assertAggregate(original, load(original));
    }

    @Test
    void versionOverflowIsSanitizedAndCannotWrapOrPartiallyWrite() {
        LegalEntity original = original(new PartyVersion(Long.MAX_VALUE));
        await(() -> persist(original));
        assertInstanceOf(ApplicationFailure.PersistenceFailure.class,
                failure(() -> repository.update(replacement(original, "Changed", null, null), original.version())));
        assertAggregate(original, load(original));
    }

    @Test
    void concurrentUpdatesUseSeparateContextsAndHaveOneWinner() throws Exception {
        LegalEntity original = original(PartyVersion.initial());
        await(() -> persist(original));
        List<Attempt> attempts = race(
                () -> repository.update(replacement(original, "First", "First Brand", "SA"), original.version()),
                () -> repository.update(replacement(original, "Second", "Second Brand", "LTD"), original.version()));
        assertEquals(1, attempts.stream().filter(Attempt::succeeded).count());
        assertEquals(1, attempts.stream()
                .filter(attempt -> attempt.failure() instanceof ApplicationFailure.ExpectedVersionMismatch).count());
        LegalEntity persisted = load(original);
        assertEquals(new PartyVersion(1), persisted.version());
        assertTrue(List.of("FIRST", "SECOND").contains(persisted.displayName()));
        assertEquals(persisted.displayName() + " BRAND", persisted.details().tradeName());
        assertEquals(persisted.displayName().equals("FIRST") ? "SA" : "LTD", persisted.details().legalFormCode());
    }

    @Test
    void detailUpdateCannotRevertAConcurrentActivation() throws Exception {
        LegalEntity original = original(PartyVersion.initial());
        await(() -> persist(original));
        await(() -> insertVerifiedIdentifier(original));
        PartyActivationCandidate activation = new PartyActivationCandidate(
                new RequestMetadata(original.tenantId(), "activator", UUID.randomUUID()),
                original.partyId(), original.version(), TODAY, UPDATED);
        List<Attempt> attempts = race(
                () -> repository.update(replacement(original, "Changed", "Brand", "SA"), original.version()),
                () -> activationPort.activate(activation));
        assertEquals(1, attempts.stream().filter(Attempt::succeeded).count());
        LegalEntity persisted = load(original);
        assertEquals(new PartyVersion(1), persisted.version());
        if (attempts.get(1).succeeded()) {
            assertEquals(PartyRecordStatus.ACTIVE, persisted.recordStatus());
            assertEquals(original.details(), persisted.details());
            assertInstanceOf(ApplicationFailure.ExpectedVersionMismatch.class, attempts.getFirst().failure());
        } else {
            assertEquals(PartyRecordStatus.DRAFT, persisted.recordStatus());
            assertEquals("CHANGED", persisted.details().legalName());
            assertInstanceOf(ApplicationFailure.StalePartyVersion.class, attempts.get(1).failure());
        }
    }

    private Uni<Void> persist(LegalEntity original) {
        return sessionFactory.withTransaction((session, transaction) -> session.persist(mapper.toEntity(original))
                .call(session::flush));
    }

    private Uni<Void> insertVerifiedIdentifier(LegalEntity original) {
        return sessionFactory.withTransaction((session, transaction) -> session.createNativeQuery("""
                insert into party_identifiers (id, tenant_id, party_id, identifier_scheme_id,
                    encrypted_value, encryption_key_version, normalized_value_hash, masked_value,
                    status, verified_at, verified_by, created_by, updated_by)
                values (:id, :tenantId, :partyId, '0198d111-08f1-7e48-b291-399bbb9cd602',
                    'unreadable-test-ciphertext', 1, :hash, '****1234', 'VERIFIED', :verifiedAt,
                    'verifier', 'creator', 'creator')
                """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("tenantId", original.tenantId().value())
                .setParameter("partyId", original.partyId().value())
                .setParameter("hash", "1".repeat(64))
                .setParameter("verifiedAt", CREATED)
                .executeUpdate().replaceWithVoid());
    }

    private Uni<Object[]> identifierState(LegalEntity original) {
        return sessionFactory.withSession(session -> session.createNativeQuery("""
                select encrypted_value, normalized_value_hash, status, version, verified_at, verified_by
                from party_identifiers where tenant_id = :tenantId and party_id = :partyId
                """, Object[].class)
                .setParameter("tenantId", original.tenantId().value())
                .setParameter("partyId", original.partyId().value())
                .getSingleResult());
    }

    private LegalEntity load(LegalEntity original) {
        return await(() -> repository.findByTenantAndId(original.tenantId(), original.partyId())).orElseThrow();
    }

    private static LegalEntity original(PartyVersion version) {
        return LegalEntity.restore(new PartyId(UUID.randomUUID()), new TenantId(UUID.randomUUID()),
                "Custom Label", PartyRecordStatus.DRAFT, version, AuditInfo.initial(CREATED, "creator"),
                new LegalEntityDetails(" Historical Legal ", " Historical Brand ", " Ltd ", "EC",
                        LocalDate.of(2020, Month.JANUARY, 15), null));
    }

    private static LegalEntity replacement(LegalEntity original, String name, String tradeName, String legalForm) {
        return original.replaceDetails(LegalEntityDetails.forWrite(name, tradeName, legalForm, "EC", null, null),
                TODAY, UPDATED, "editor");
    }

    private static void assertAggregate(LegalEntity expected, LegalEntity actual) {
        assertEquals(expected.partyId(), actual.partyId());
        assertEquals(expected.tenantId(), actual.tenantId());
        assertEquals(expected.type(), actual.type());
        assertEquals(expected.recordStatus(), actual.recordStatus());
        assertEquals(expected.displayName(), actual.displayName());
        assertEquals(expected.version(), actual.version());
        assertEquals(expected.auditInfo(), actual.auditInfo());
        assertEquals(expected.details(), actual.details());
    }

    private static ApplicationFailure failure(Supplier<? extends Uni<?>> operation) {
        return assertThrows(ApplicationException.class, () -> await(() -> operation.get().replaceWithVoid())).failure();
    }

    private static <T> T await(Supplier<Uni<T>> operation) {
        try {
            return VertxContextSupport.subscribeAndAwait(() -> operation.get().ifNoItem().after(WAIT).fail());
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new AssertionError("Reactive test operation failed", failure);
        }
    }

    private static List<Attempt> race(
            Supplier<? extends Uni<?>> first, Supplier<? extends Uni<?>> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> attempt(ready, start, first));
            var two = executor.submit(() -> attempt(ready, start, second));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        }
    }

    private static Attempt attempt(CountDownLatch ready, CountDownLatch start,
            Supplier<? extends Uni<?>> operation) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            await(() -> operation.get().replaceWithVoid());
            return new Attempt(true, null);
        } catch (ApplicationException failure) {
            return new Attempt(false, failure.failure());
        }
    }

    /**
     * Captures each independently-contextualized concurrent outcome for assertions.
     */
    private record Attempt(boolean succeeded, ApplicationFailure failure) {
    }

    /**
     * Forces a late mapping failure without adding a production failure hook or CDI
     * bean.
     */
    @Vetoed
    private static final class FailingReloadMapper extends LegalEntityPersistenceMapper {
        @Override
        LegalEntity toDomain(PartyEntity party, LegalEntityDetailsEntity details) {
            throw new IllegalStateException("Controlled reload failure");
        }
    }
}
