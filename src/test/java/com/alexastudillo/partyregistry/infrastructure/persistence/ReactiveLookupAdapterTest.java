package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.port.IdentifierSchemeRepository;
import com.alexastudillo.partyregistry.application.port.PartyLookupPort;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.support.IdentifierSchemeTestFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies tenant-safe reactive scheme and Party lookup against PostgreSQL fixtures.
 */
@QuarkusTest
@Timeout(30)
class ReactiveLookupAdapterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-04T10:15:30.123456Z");

    @Inject
    IdentifierSchemeRepository schemeRepository;

    @Inject
    PartyLookupPort partyLookupPort;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    @RunOnVertxContext
    void mapsEverySchemeLifecycleSubjectRuleAndExpirationFixture(UniAsserter asserter) {
        assertScheme(asserter, new SchemeExpectation(
                IdentifierSchemeTestFixtures.NATURAL_ACTIVE_ID,
                IdentifierSchemeTestFixtures.NATURAL_ACTIVE_CODE,
                IdentifierCategory.NATIONAL_ID,
                IdentifierSubjectType.NATURAL_PERSON,
                "Test natural-person identifier",
                "Active test-only scheme for natural persons.",
                6,
                20,
                false,
                IdentifierSchemeStatus.ACTIVE));
        assertScheme(asserter, new SchemeExpectation(
                IdentifierSchemeTestFixtures.LEGAL_ACTIVE_ID,
                IdentifierSchemeTestFixtures.LEGAL_ACTIVE_CODE,
                IdentifierCategory.LEGAL_REGISTRATION_NUMBER,
                IdentifierSubjectType.LEGAL_ENTITY,
                "Test legal-entity identifier",
                "Active test-only scheme for legal entities.",
                6,
                24,
                false,
                IdentifierSchemeStatus.ACTIVE));
        assertScheme(asserter, new SchemeExpectation(
                IdentifierSchemeTestFixtures.BOTH_EXPIRING_ID,
                IdentifierSchemeTestFixtures.BOTH_EXPIRING_CODE,
                IdentifierCategory.PASSPORT,
                IdentifierSubjectType.BOTH,
                "Test expiring identifier",
                "Active test-only scheme requiring expiration.",
                6,
                16,
                true,
                IdentifierSchemeStatus.ACTIVE));
        assertScheme(asserter, new SchemeExpectation(
                IdentifierSchemeTestFixtures.BOTH_DRAFT_ID,
                IdentifierSchemeTestFixtures.BOTH_DRAFT_CODE,
                IdentifierCategory.OTHER,
                IdentifierSubjectType.BOTH,
                "Test draft identifier",
                "Inactive test-only draft scheme.",
                1,
                32,
                false,
                IdentifierSchemeStatus.DRAFT));
        assertScheme(asserter, new SchemeExpectation(
                IdentifierSchemeTestFixtures.BOTH_DEPRECATED_ID,
                IdentifierSchemeTestFixtures.BOTH_DEPRECATED_CODE,
                IdentifierCategory.OTHER,
                IdentifierSubjectType.BOTH,
                "Test deprecated identifier",
                "Inactive test-only deprecated scheme.",
                1,
                32,
                false,
                IdentifierSchemeStatus.DEPRECATED));
        assertScheme(asserter, new SchemeExpectation(
                IdentifierSchemeTestFixtures.BOTH_RETIRED_ID,
                IdentifierSchemeTestFixtures.BOTH_RETIRED_CODE,
                IdentifierCategory.OTHER,
                IdentifierSubjectType.BOTH,
                "Test retired identifier",
                "Inactive test-only retired scheme.",
                1,
                32,
                false,
                IdentifierSchemeStatus.RETIRED));
    }

    @Test
    @RunOnVertxContext
    void requiresAnExactSchemeCodeAndReturnsEmptyForMissingSchemes(UniAsserter asserter) {
        asserter.assertThat(
                () -> schemeRepository.findByCode("test_natural_active"),
                found -> assertTrue(found.isEmpty()));
        asserter.assertThat(
                () -> schemeRepository.findByCode("TEST_MISSING_SCHEME"),
                found -> assertTrue(found.isEmpty()));
    }

    @Test
    @RunOnVertxContext
    void resolvesNaturalAndLegalTypesAndConcealsCrossTenantParties(UniAsserter asserter) {
        TenantId tenantId = tenantId();
        TenantId otherTenantId = tenantId();
        PartyId naturalPersonId = partyId();
        PartyId legalEntityId = partyId();
        PartyId missingPartyId = partyId();

        asserter.execute(() -> persistParties(tenantId, naturalPersonId, legalEntityId));
        asserter.assertThat(
                () -> partyLookupPort.findType(tenantId, naturalPersonId),
                found -> assertEquals(PartyType.NATURAL_PERSON, found.orElseThrow()));
        asserter.assertThat(
                () -> partyLookupPort.findType(tenantId, legalEntityId),
                found -> assertEquals(PartyType.LEGAL_ENTITY, found.orElseThrow()));
        asserter.assertThat(
                () -> partyLookupPort.findType(otherTenantId, naturalPersonId),
                found -> assertTrue(found.isEmpty()));
        asserter.assertThat(
                () -> partyLookupPort.findType(tenantId, missingPartyId),
                found -> assertTrue(found.isEmpty()));
    }

    private void assertScheme(UniAsserter asserter, SchemeExpectation expected) {
        asserter.assertThat(
                () -> schemeRepository.findByCode(expected.code()),
                found -> assertSchemeEquals(expected, found.orElseThrow()));
    }

    private Uni<Void> persistParties(
            TenantId tenantId,
            PartyId naturalPersonId,
            PartyId legalEntityId) {
        PartyEntity naturalPerson = partyEntity(tenantId, naturalPersonId, PartyType.NATURAL_PERSON);
        PartyEntity legalEntity = partyEntity(tenantId, legalEntityId, PartyType.LEGAL_ENTITY);
        return sessionFactory.withTransaction((session, transaction) -> session.persist(naturalPerson)
                .call(() -> session.persist(legalEntity))
                .call(session::flush));
    }

    private static PartyEntity partyEntity(TenantId tenantId, PartyId partyId, PartyType type) {
        return new PartyEntity(
                partyId.value(),
                tenantId.value(),
                type,
                type == PartyType.NATURAL_PERSON ? "Lookup Natural Person" : "Lookup Legal Entity",
                PartyRecordStatus.DRAFT,
                AuditInfo.initial(CREATED_AT, "lookup-test"),
                0);
    }

    private static void assertSchemeEquals(SchemeExpectation expected, IdentifierScheme actual) {
        assertEquals(expected.id(), actual.id().value());
        assertEquals(expected.code(), actual.code());
        assertEquals("EC", actual.issuingCountryCode());
        assertEquals(expected.category(), actual.category());
        assertEquals(expected.subjectType(), actual.applicableSubjectType());
        assertEquals(expected.name(), actual.name());
        assertEquals(expected.description(), actual.description());
        assertEquals("TRIM_UPPERCASE_V1", actual.normalizerKey());
        assertEquals("ALPHANUMERIC_V1", actual.validatorKey());
        assertEquals(expected.minimumLength(), actual.minimumLength());
        assertEquals(expected.maximumLength(), actual.maximumLength());
        assertEquals(expected.requiresExpiration(), actual.requiresExpiration());
        assertEquals(expected.status(), actual.status());
        assertEquals(IdentifierSchemeVersion.initial(), actual.version());
        assertNotNull(actual.auditInfo().createdAt());
        assertEquals("test-fixture", actual.auditInfo().createdBy());
        assertNotNull(actual.auditInfo().updatedAt());
        assertEquals("test-fixture", actual.auditInfo().updatedBy());
        assertFalse(actual.auditInfo().updatedAt().isBefore(actual.auditInfo().createdAt()));
    }

    private static TenantId tenantId() {
        return new TenantId(UUID.randomUUID());
    }

    private static PartyId partyId() {
        return new PartyId(UUID.randomUUID());
    }

    /**
     * Describes the complete test-catalog projection expected from one lookup.
     */
    private record SchemeExpectation(
            UUID id,
            String code,
            IdentifierCategory category,
            IdentifierSubjectType subjectType,
            String name,
            String description,
            Integer minimumLength,
            Integer maximumLength,
            boolean requiresExpiration,
            IdentifierSchemeStatus status) {
    }
}
