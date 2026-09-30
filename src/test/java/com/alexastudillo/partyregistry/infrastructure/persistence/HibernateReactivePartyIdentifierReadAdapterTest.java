package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.persistence.PersistenceException;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the scalar projection, query boundaries, and neutral failures without a
 * database.
 */
class HibernateReactivePartyIdentifierReadAdapterTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final TenantId TENANT_ID = new TenantId(UUID.randomUUID());
    private static final PartyId PARTY_ID = new PartyId(UUID.randomUUID());
    private static final LocalDate EVALUATED_ON = LocalDate.of(2026, Month.SEPTEMBER, 12);

    @Test
    void selectsExactlyTheSafeResultColumnsAndOnlyCurrentTenantPartyRows() {
        String query = HibernateReactivePartyIdentifierReadAdapter.FIND_CURRENT_BY_PARTY
                .replaceAll("\\s+", " ").trim();
        String projection = query.substring("select ".length(), query.indexOf(" from "));

        assertEquals(List.of(
                "identifier.id", "identifier.partyId", "identifier.identifierSchemeId",
                "scheme.code", "identifier.maskedValue", "identifier.status", "identifier.primary",
                "identifier.issuerCode", "identifier.issuedOn", "identifier.expiresOn",
                "identifier.verifiedAt", "identifier.verifiedBy", "identifier.version",
                "identifier.createdAt", "identifier.updatedAt"),
                Arrays.asList(projection.split(",\\s*")));
        assertEquals("from PartyIdentifierEntity identifier "
                + "join IdentifierSchemeEntity scheme on scheme.id = identifier.identifierSchemeId "
                + "where identifier.tenantId = :tenantId "
                + "and identifier.partyId = :partyId "
                + "and identifier.status in (:pendingVerification, :verified) "
                + "and (identifier.expiresOn is null or identifier.expiresOn >= :evaluatedOn) "
                + "order by identifier.createdAt asc, identifier.id asc",
                query.substring(query.indexOf("from ")));
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (String forbidden : List.of("encrypted", "cipher", "hash", "keyversion", "normalization")) {
            assertFalse(lowerQuery.contains(forbidden));
        }
    }

    @Test
    void hasNoEntityOrProtectionDependencyAndUsesOneUnboundedResultListQuery() {
        var adapter = new ClassFileImporter()
                .importClasses(HibernateReactivePartyIdentifierReadAdapter.class)
                .get(HibernateReactivePartyIdentifierReadAdapter.class);
        assertFalse(adapter.getDirectDependenciesFromSelf().stream()
                .map(dependency -> dependency.getTargetClass().getName())
                .anyMatch(name -> name.endsWith("Entity")
                        || name.contains("IdentifierProtection")
                        || name.contains("ProtectedIdentifierValue")
                        || name.startsWith("java.lang.reflect.")
                        || name.startsWith("javax.crypto.")));
        var calls = adapter.getMethodCallsFromSelf();
        assertEquals(1L, calls.stream()
                .filter(call -> call.getTarget().getName().equals("createQuery")).count());
        assertEquals(1L, calls.stream()
                .filter(call -> call.getTarget().getName().equals("getResultList")).count());
        Set<String> forbiddenCalls = Set.of(
                "setMaxResults", "setFirstResult", "find", "fetch", "createNativeQuery",
                "getSingleResult", "getSingleResultOrNull", "withTransaction");
        assertTrue(calls.stream().noneMatch(call -> forbiddenCalls.contains(call.getTarget().getName())));
    }

    @Test
    void mapsAllScalarPositionsIncludingNullableMetadataWithoutProtectedMaterial() {
        UUID identifierId = UUID.randomUUID();
        UUID schemeId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-01T10:00:00.123456Z");
        Instant updatedAt = createdAt.plusSeconds(30);
        Instant verifiedAt = createdAt.plusSeconds(10);
        Object[] row = {
                identifierId, PARTY_ID.value(), schemeId, "TEST_SCHEME", "********1234",
                PartyIdentifierStatus.VERIFIED, true, "TEST-ISSUER", EVALUATED_ON.minusYears(1),
                EVALUATED_ON, verifiedAt, "test-verifier", 7L, createdAt, updatedAt
        };
        assertEquals(new PartyIdentifierResult(
                new PartyIdentifierId(identifierId), PARTY_ID, new IdentifierSchemeId(schemeId),
                "TEST_SCHEME", "********1234", PartyIdentifierStatus.VERIFIED, true,
                "TEST-ISSUER", EVALUATED_ON.minusYears(1), EVALUATED_ON, verifiedAt,
                "test-verifier", new PartyIdentifierVersion(7), createdAt, updatedAt),
                HibernateReactivePartyIdentifierReadAdapter.toResult(row));

        row[5] = PartyIdentifierStatus.PENDING_VERIFICATION;
        row[6] = false;
        Arrays.fill(row, 7, 12, null);
        PartyIdentifierResult nullable = HibernateReactivePartyIdentifierReadAdapter.toResult(row);
        assertEquals(PartyIdentifierStatus.PENDING_VERIFICATION, nullable.status());
        assertFalse(nullable.isPrimary());
        assertNull(nullable.issuerCode());
        assertNull(nullable.issuedOn());
        assertNull(nullable.expiresOn());
        assertNull(nullable.verifiedAt());
        assertNull(nullable.verifiedBy());
    }

    @Test
    void translatesPersistenceFailuresAndPreservesApplicationFailures() {
        PersistenceException databaseFailure = new PersistenceException("sensitive database detail");
        ApplicationException translated = assertInstanceOf(
                ApplicationException.class, readFailure(databaseFailure));
        assertInstanceOf(ApplicationFailure.PersistenceFailure.class, translated.failure());
        assertSame(databaseFailure, translated.getCause());
        assertFalse(translated.getMessage().contains("sensitive"));

        ApplicationException applicationFailure = new ApplicationException(
                new ApplicationFailure.PersistenceFailure());
        assertSame(applicationFailure, readFailure(applicationFailure));
    }

    @Test
    void propagatesCancellationToTheSessionOperation() {
        AtomicBoolean cancelled = new AtomicBoolean();
        Mutiny.SessionFactory sessionFactory = (Mutiny.SessionFactory) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { Mutiny.SessionFactory.class },
                (_, method, _) -> {
                    if (method.getName().equals("withSession")) {
                        return Uni.createFrom().nothing().onCancellation()
                                .invoke(() -> cancelled.set(true));
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        var subscriber = new HibernateReactivePartyIdentifierReadAdapter(sessionFactory)
                .findCurrentByParty(TENANT_ID, PARTY_ID, EVALUATED_ON)
                .subscribe().withSubscriber(UniAssertSubscriber.create());
        subscriber.awaitSubscription(TIMEOUT).cancel();
        assertTrue(cancelled.get());
    }

    private static Throwable readFailure(Throwable failure) {
        Mutiny.SessionFactory sessionFactory = (Mutiny.SessionFactory) Proxy.newProxyInstance(
                HibernateReactivePartyIdentifierReadAdapterTest.class.getClassLoader(),
                new Class<?>[] { Mutiny.SessionFactory.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("withSession")) {
                        return Uni.createFrom().failure(failure);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return new HibernateReactivePartyIdentifierReadAdapter(sessionFactory)
                .findCurrentByParty(TENANT_ID, PARTY_ID, EVALUATED_ON)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure(TIMEOUT)
                .getFailure();
    }
}
