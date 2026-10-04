package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.testing.PartyIdentifierFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers every independent identifier transition, qualification, attribution, and retained history.
 * Keeps lifecycle decisions independent of replay, persistence, and external verification.
 */
class PartyIdentifierLifecycleTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING_VERIFICATION, VERIFY, VERIFIED", "PENDING_VERIFICATION, REJECT, REJECTED", "PENDING_VERIFICATION, REVOKE, FORBIDDEN",
            "VERIFIED, VERIFY, FORBIDDEN", "VERIFIED, REJECT, FORBIDDEN", "VERIFIED, REVOKE, REVOKED",
            "REJECTED, VERIFY, FORBIDDEN", "REJECTED, REJECT, FORBIDDEN", "REJECTED, REVOKE, FORBIDDEN",
            "EXPIRED, VERIFY, FORBIDDEN", "EXPIRED, REJECT, FORBIDDEN", "EXPIRED, REVOKE, FORBIDDEN",
            "REVOKED, VERIFY, FORBIDDEN", "REVOKED, REJECT, FORBIDDEN", "REVOKED, REVOKE, FORBIDDEN"
    })
    void enforcesEveryMatrixEntryForBothPartyTypes(PartyIdentifierStatus status, Action action, String expected) {
        for (PartyType type : PartyType.values()) {
            var current = PartyIdentifierFixtures.identifier(type, status, true, 5, PartyIdentifierFixtures.EVALUATED_ON);
            if (expected.equals("FORBIDDEN")) {
                var failure = assertThrows(DomainValidationException.class,
                        () -> change(current, action, PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR));
                assertEquals(DomainViolation.PARTY_IDENTIFIER_INVALID_LIFECYCLE, failure.violation());
                assertEquals(5, current.version().value());
                assertTrue(current.isPrimary());
            } else {
                var changed = change(current, action, PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
                assertEquals(PartyIdentifierStatus.valueOf(expected), changed.status());
                assertEquals(6, changed.version().value());
                assertEquals(action == Action.VERIFY, changed.isPrimary());
                assertEquals(PartyIdentifierFixtures.OCCURRED_AT, changed.auditInfo().updatedAt());
                assertEquals(PartyIdentifierFixtures.ACTOR, changed.auditInfo().updatedBy());
                assertHistory(current, changed);
                assertEquals(status, current.status());
            }
        }
    }

    @Test
    void verificationUsesTheAcceptedActorAndSameInstantWithoutChangingMetadataOrFlag() {
        var current = PartyIdentifierFixtures.identifier(PartyType.LEGAL_ENTITY,
                PartyIdentifierStatus.PENDING_VERIFICATION, true, 0, PartyIdentifierFixtures.EVALUATED_ON);
        var verified = current.verify(PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
        assertEquals(PartyIdentifierFixtures.OCCURRED_AT, verified.verifiedAt());
        assertEquals(PartyIdentifierFixtures.ACTOR, verified.verifiedBy());
        assertEquals(verified.verifiedAt(), verified.auditInfo().updatedAt());
        assertEquals(verified.verifiedBy(), verified.auditInfo().updatedBy());
        assertTrue(verified.isPrimary());
        assertEquals(current.issuerCode(), verified.issuerCode());
        assertEquals(current.issuedOn(), verified.issuedOn());
        assertEquals(current.expiresOn(), verified.expiresOn());
        assertHistory(current, verified);
    }

    @Test
    void verificationValidityUsesInclusiveUtcDateAndAllowsNoExpiration() {
        Instant beforeMidnight = PartyIdentifierFixtures.EVALUATED_ON.atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
        Instant afterMidnight = PartyIdentifierFixtures.EVALUATED_ON.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var inclusive = PartyIdentifierFixtures.identifier(PartyType.NATURAL_PERSON,
                PartyIdentifierStatus.PENDING_VERIFICATION, false, 0, PartyIdentifierFixtures.EVALUATED_ON);
        assertEquals(PartyIdentifierStatus.VERIFIED, inclusive.verify(beforeMidnight, PartyIdentifierFixtures.ACTOR).status());
        var failure = assertThrows(DomainValidationException.class, () -> inclusive.verify(afterMidnight, PartyIdentifierFixtures.ACTOR));
        assertEquals(DomainViolation.IDENTIFIER_EXPIRED, failure.violation());
        var noExpiration = PartyIdentifierFixtures.identifier(PartyType.NATURAL_PERSON,
                PartyIdentifierStatus.PENDING_VERIFICATION, false, 0, null);
        assertEquals(PartyIdentifierStatus.VERIFIED, noExpiration.verify(afterMidnight, PartyIdentifierFixtures.ACTOR).status());
        assertFalse(noExpiration.verify(afterMidnight, PartyIdentifierFixtures.ACTOR).isPrimary());
    }

    @Test
    void withdrawalAllowsPastDatesClearsFlagsAndRetainsPriorVerificationEvidence() {
        LocalDate past = PartyIdentifierFixtures.EVALUATED_ON.minusDays(1);
        var pending = PartyIdentifierFixtures.identifier(PartyType.NATURAL_PERSON, PartyIdentifierStatus.PENDING_VERIFICATION, true, 2, past);
        var rejected = pending.reject(PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
        assertEquals(PartyIdentifierStatus.REJECTED, rejected.status());
        assertFalse(rejected.isPrimary());
        assertEquals(past, rejected.expiresOn());
        var current = PartyIdentifierFixtures.identifier(PartyType.LEGAL_ENTITY, PartyIdentifierStatus.VERIFIED, true, 2, past);
        var revoked = current.revoke(PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
        assertEquals(PartyIdentifierStatus.REVOKED, revoked.status());
        assertFalse(revoked.isPrimary());
        assertEquals(current.verifiedAt(), revoked.verifiedAt());
        assertEquals(current.verifiedBy(), revoked.verifiedBy());
        assertEquals(past, revoked.expiresOn());
        assertHistory(current, revoked);
    }

    @Test
    void currentStatePrecedesExhaustionAndPermittedExhaustionPrecedesQualification() {
        var forbidden = PartyIdentifierFixtures.identifier(PartyType.NATURAL_PERSON,
                PartyIdentifierStatus.REVOKED, true, Long.MAX_VALUE, PartyIdentifierFixtures.EVALUATED_ON.minusDays(1));
        var stateFailure = assertThrows(DomainValidationException.class,
                () -> forbidden.verify(PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR));
        assertEquals(DomainViolation.PARTY_IDENTIFIER_INVALID_LIFECYCLE, stateFailure.violation());
        var exhausted = PartyIdentifierFixtures.identifier(PartyType.NATURAL_PERSON,
                PartyIdentifierStatus.PENDING_VERIFICATION, true, Long.MAX_VALUE, PartyIdentifierFixtures.EVALUATED_ON.minusDays(1));
        var versionFailure = assertThrows(DomainValidationException.class,
                () -> exhausted.verify(PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR));
        assertEquals(DomainViolation.PARTY_IDENTIFIER_VERSION_OVERFLOW, versionFailure.violation());
    }

    @Test
    void repeatingAnAcceptedTransitionWithoutHistoricalReplayIsNotANoop() {
        var pending = PartyIdentifierFixtures.identifier(PartyType.NATURAL_PERSON,
                PartyIdentifierStatus.PENDING_VERIFICATION, false, 0, null);
        var verified = pending.verify(PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
        var rejected = pending.reject(PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
        var revoked = verified.revoke(PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
        for (var current : List.of(verified, rejected, revoked)) {
            var failure = assertThrows(DomainValidationException.class,
                    () -> current.verify(PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR));
            assertEquals(DomainViolation.PARTY_IDENTIFIER_INVALID_LIFECYCLE, failure.violation());
        }
        assertThrows(DomainValidationException.class, () -> rejected.reject(PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR));
        assertThrows(DomainValidationException.class, () -> revoked.revoke(PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR));
    }

    private static PartyIdentifier change(PartyIdentifier current, Action action, Instant instant, String actor) {
        return switch (action) {
            case VERIFY -> current.verify(instant, actor);
            case REJECT -> current.reject(instant, actor);
            case REVOKE -> current.revoke(instant, actor);
        };
    }

    private static void assertHistory(PartyIdentifier current, PartyIdentifier changed) {
        assertEquals(current.identifierId(), changed.identifierId());
        assertEquals(current.tenantId(), changed.tenantId());
        assertEquals(current.partyId(), changed.partyId());
        assertEquals(current.identifierSchemeId(), changed.identifierSchemeId());
        assertSame(current.protectedValue(), changed.protectedValue());
        assertEquals(current.auditInfo().createdAt(), changed.auditInfo().createdAt());
        assertEquals(current.auditInfo().createdBy(), changed.auditInfo().createdBy());
    }

    /** Names the three synchronous Domain lifecycle operations exercised by the closed matrix. */
    private enum Action {
        VERIFY, REJECT, REVOKE
    }
}
