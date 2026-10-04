package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierPrimaryPlan;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.testing.PartyIdentifierFixtures;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies scoped primary decisions and immutable replacement plans before any persistence can occur. */
class PartyIdentifierPrimaryPolicyTest {

    private final PartyIdentifierPrimaryPolicy policy = new PartyIdentifierPrimaryPolicy();

    @Test
    void onlyVerifiedCandidatesQualifyAndExpirationIsInclusiveForBothOwnerTypes() {
        for (PartyType type : PartyType.values()) {
            for (PartyIdentifierStatus status : PartyIdentifierStatus.values()) {
                if (status == PartyIdentifierStatus.VERIFIED) continue;
                var candidate = candidate(type, status, false, 3, PartyIdentifierFixtures.EVALUATED_ON);
                var failure = assertThrows(DomainValidationException.class, () -> select(candidate, null, false));
                assertEquals(DomainViolation.PARTY_IDENTIFIER_INVALID_LIFECYCLE, failure.violation());
                assertFalse(candidate.isPrimary());
                assertEquals(3, candidate.version().value());
            }
            for (LocalDate expiration : new LocalDate[]{null, PartyIdentifierFixtures.EVALUATED_ON}) {
                var candidate = candidate(type, PartyIdentifierStatus.VERIFIED, false, 3, expiration);
                var plan = select(candidate, null, false);
                assertTrue(plan.candidate().isPrimary());
                assertEquals(4, plan.candidate().version().value());
                assertNull(plan.formerPrimary());
            }
            var expired = candidate(type, PartyIdentifierStatus.VERIFIED, false, 3,
                    PartyIdentifierFixtures.EVALUATED_ON.minusDays(1));
            var failure = assertThrows(DomainValidationException.class, () -> select(expired, null, false));
            assertEquals(DomainViolation.IDENTIFIER_EXPIRED, failure.violation());
        }
    }

    @Test
    void replacementRequiresExplicitConsentAndDemotesEvenPastDatePrimaries() {
        for (PartyType type : PartyType.values()) {
            var candidate = candidate(type, PartyIdentifierStatus.VERIFIED, false, 3, null);
            var former = other(candidate, PartyIdentifierStatus.VERIFIED, true, 8,
                    PartyIdentifierFixtures.EVALUATED_ON.minusDays(1));
            var failure = assertThrows(DomainValidationException.class, () -> select(candidate, former, false));
            assertEquals(DomainViolation.PRIMARY_IDENTIFIER_CONFLICT, failure.violation());
            var plan = select(candidate, former, true);
            assertTrue(plan.candidate().isPrimary());
            var demoted = plan.formerPrimary();
            assertNotNull(demoted);
            assertFalse(demoted.isPrimary());
            assertEquals(4, plan.candidate().version().value());
            assertEquals(9, demoted.version().value());
            assertPreserved(candidate, plan.candidate());
            assertPreserved(former, demoted);
            assertFalse(candidate.isPrimary());
            assertTrue(former.isPrimary());
        }
    }

    @Test
    void alreadyPrimarySelectionAdvancesOnlyTheTargetForEitherReplacementChoice() {
        var current = candidate(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, true, 3, null);
        for (boolean replace : List.of(false, true)) {
            var plan = select(current, current, replace);
            assertTrue(plan.candidate().isPrimary());
            assertEquals(4, plan.candidate().version().value());
            assertNull(plan.formerPrimary());
            assertPreserved(current, plan.candidate());
        }
        assertEquals(3, current.version().value());
    }

    @Test
    void provisionalNonprimaryAndUnrelatedScopeRecordsAreNotDisplaced() {
        var candidate = candidate(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, false, 3, null);
        var verified = other(candidate, PartyIdentifierStatus.VERIFIED, true, 8, null);
        var provisional = other(candidate, PartyIdentifierStatus.PENDING_VERIFICATION, true, 8, null);
        var nonprimary = other(candidate, PartyIdentifierStatus.VERIFIED, false, 8, null);
        var anotherTenant = copy(verified).tenantId(new TenantId(UUID.randomUUID())).build();
        var anotherParty = copy(verified).partyId(new PartyId(UUID.randomUUID())).build();
        var anotherScheme = copy(verified).identifierSchemeId(new IdentifierSchemeId(UUID.randomUUID())).build();
        for (var unrelated : List.of(provisional, nonprimary, anotherTenant, anotherParty, anotherScheme)) {
            var plan = select(candidate, unrelated, true);
            assertNull(plan.formerPrimary());
            assertTrue(plan.candidate().isPrimary());
            assertEquals(8, unrelated.version().value());
        }
    }

    @Test
    void verificationConflictsOnlyForProvisionalCandidatesWithAnotherScopedVerifiedPrimary() {
        var pending = candidate(PartyType.NATURAL_PERSON, PartyIdentifierStatus.PENDING_VERIFICATION, true, 3, null);
        var former = other(pending, PartyIdentifierStatus.VERIFIED, true, 8,
                PartyIdentifierFixtures.EVALUATED_ON.minusDays(1));
        var failure = assertThrows(DomainValidationException.class, () -> verify(pending, former));
        assertEquals(DomainViolation.PRIMARY_IDENTIFIER_CONFLICT, failure.violation());
        assertEquals(PartyIdentifierStatus.PENDING_VERIFICATION, pending.status());
        assertNull(pending.verifiedAt());
        assertEquals(3, pending.version().value());
        assertTrue(former.isPrimary());
        var nonprimary = copy(pending).primary(false).build();
        assertFalse(verify(nonprimary, former).isPrimary());
        assertTrue(verify(pending, null).isPrimary());
        var provisional = copy(former).status(PartyIdentifierStatus.PENDING_VERIFICATION).build();
        assertTrue(verify(pending, provisional).isPrimary());
        var unrelated = copy(former).identifierSchemeId(new IdentifierSchemeId(UUID.randomUUID())).build();
        assertTrue(verify(pending, unrelated).isPrimary());
    }

    @Test
    void targetStateThenExhaustionThenValidityPrecedePrimaryConflict() {
        var terminal = candidate(PartyType.NATURAL_PERSON, PartyIdentifierStatus.REVOKED, false, Long.MAX_VALUE,
                PartyIdentifierFixtures.EVALUATED_ON.minusDays(1));
        var exhausted = copy(terminal).status(PartyIdentifierStatus.VERIFIED).build();
        var expired = copy(exhausted).version(new PartyIdentifierVersion(3)).build();
        var former = other(terminal, PartyIdentifierStatus.VERIFIED, true, 8, null);
        assertViolation(DomainViolation.PARTY_IDENTIFIER_INVALID_LIFECYCLE, terminal, former);
        assertViolation(DomainViolation.PARTY_IDENTIFIER_VERSION_OVERFLOW, exhausted, former);
        assertViolation(DomainViolation.IDENTIFIER_EXPIRED, expired, former);
        var pending = copy(exhausted).status(PartyIdentifierStatus.PENDING_VERIFICATION).primary(true).build();
        var failure = assertThrows(DomainValidationException.class, () -> verify(pending, former));
        assertEquals(DomainViolation.PARTY_IDENTIFIER_VERSION_OVERFLOW, failure.violation());
    }

    @Test
    void planConstructionRejectsIncompleteFlagsDuplicateIdentityAndCrossScopePairs() {
        var candidate = candidate(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, true, 3, null);
        var former = other(candidate, PartyIdentifierStatus.VERIFIED, false, 8, null);
        var pending = copy(candidate).status(PartyIdentifierStatus.PENDING_VERIFICATION).build();
        var nonprimary = copy(candidate).primary(false).build();
        var stillPrimary = copy(former).primary(true).build();
        var duplicate = copy(candidate).primary(false).build();
        var unrelated = copy(former).partyId(new PartyId(UUID.randomUUID())).build();
        assertThrows(IllegalArgumentException.class, () -> new PartyIdentifierPrimaryPlan(pending, null));
        assertThrows(IllegalArgumentException.class, () -> new PartyIdentifierPrimaryPlan(nonprimary, null));
        assertThrows(IllegalArgumentException.class, () -> new PartyIdentifierPrimaryPlan(candidate, stillPrimary));
        assertThrows(IllegalArgumentException.class, () -> new PartyIdentifierPrimaryPlan(candidate, duplicate));
        assertThrows(IllegalArgumentException.class, () -> new PartyIdentifierPrimaryPlan(candidate, unrelated));
        assertSame(former, new PartyIdentifierPrimaryPlan(candidate, former).formerPrimary());
    }

    @Test
    void formerExhaustionRejectsTheWholeReplacementButNotNonreplacementOrVerification() {
        var candidate = candidate(PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, false, 3, null);
        var former = other(candidate, PartyIdentifierStatus.VERIFIED, true, Long.MAX_VALUE, null);
        var failure = assertThrows(DomainValidationException.class, () -> select(candidate, former, true));
        assertEquals(DomainViolation.PARTY_IDENTIFIER_VERSION_OVERFLOW, failure.violation());
        assertFalse(candidate.isPrimary());
        assertTrue(former.isPrimary());
        assertEquals(3, candidate.version().value());
        assertEquals(Long.MAX_VALUE, former.version().value());
        assertViolation(DomainViolation.PRIMARY_IDENTIFIER_CONFLICT, candidate, former);
        var pending = copy(candidate).status(PartyIdentifierStatus.PENDING_VERIFICATION).build();
        assertEquals(PartyIdentifierStatus.VERIFIED, verify(pending, former).status());
    }

    private PartyIdentifierPrimaryPlan select(PartyIdentifier candidate, PartyIdentifier former, boolean replace) {
        return policy.planSelection(candidate, former, replace, PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
    }

    private PartyIdentifier verify(PartyIdentifier candidate, PartyIdentifier former) {
        return policy.verify(candidate, former, PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
    }

    private void assertViolation(DomainViolation expected, PartyIdentifier candidate, PartyIdentifier former) {
        var failure = assertThrows(DomainValidationException.class, () -> select(candidate, former, false));
        assertEquals(expected, failure.violation());
    }

    private static void assertPreserved(PartyIdentifier before, PartyIdentifier after) {
        assertEquals(before.identifierId(), after.identifierId());
        assertEquals(before.tenantId(), after.tenantId());
        assertEquals(before.partyId(), after.partyId());
        assertEquals(before.identifierSchemeId(), after.identifierSchemeId());
        assertSame(before.protectedValue(), after.protectedValue());
        assertEquals(before.issuerCode(), after.issuerCode());
        assertEquals(before.issuedOn(), after.issuedOn());
        assertEquals(before.expiresOn(), after.expiresOn());
        assertEquals(before.status(), after.status());
        assertEquals(before.verifiedAt(), after.verifiedAt());
        assertEquals(before.verifiedBy(), after.verifiedBy());
        assertEquals(before.auditInfo().createdAt(), after.auditInfo().createdAt());
        assertEquals(before.auditInfo().createdBy(), after.auditInfo().createdBy());
        assertEquals(PartyIdentifierFixtures.OCCURRED_AT, after.auditInfo().updatedAt());
        assertEquals(PartyIdentifierFixtures.ACTOR, after.auditInfo().updatedBy());
    }

    private static PartyIdentifier candidate(PartyType type, PartyIdentifierStatus status, boolean primary,
            long version, LocalDate expiration) {
        return PartyIdentifierFixtures.identifier(type, status, primary, version, expiration);
    }

    private static PartyIdentifier other(PartyIdentifier candidate, PartyIdentifierStatus status, boolean primary,
            long version, LocalDate expiration) {
        return copy(candidate).identifierId(new PartyIdentifierId(UUID.randomUUID())).status(status).primary(primary)
                .verifiedAt(PartyIdentifierFixtures.CREATED_AT).verifiedBy(PartyIdentifierFixtures.CREATOR)
                .version(new PartyIdentifierVersion(version)).expiresOn(expiration).build();
    }

    private static PartyIdentifier.Builder copy(PartyIdentifier value) {
        return PartyIdentifier.builder().identifierId(value.identifierId()).tenantId(value.tenantId()).partyId(value.partyId())
                .identifierSchemeId(value.identifierSchemeId()).protectedValue(value.protectedValue()).issuerCode(value.issuerCode())
                .issuedOn(value.issuedOn()).expiresOn(value.expiresOn()).primary(value.isPrimary()).status(value.status())
                .verifiedAt(value.verifiedAt()).verifiedBy(value.verifiedBy()).version(value.version()).auditInfo(value.auditInfo());
    }
}
