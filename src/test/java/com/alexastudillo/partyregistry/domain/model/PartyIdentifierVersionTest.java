package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.testing.PartyIdentifierFixtures;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Checks independent version capacity and immutable audit preparation before identifier mutations. */
class PartyIdentifierVersionTest {

    @Test
    void advancesExactlyOnceWithoutChangingThePriorVersion() {
        for (long value : List.of(0L, 41L, Long.MAX_VALUE - 1)) {
            var current = new PartyIdentifierVersion(value);
            var next = current.next();
            assertEquals(value + 1, next.value());
            assertEquals(value, current.value());
            assertNotSame(current, next);
        }
    }

    @Test
    void maximumSupportedVersionFailsWithNeutralExhaustionBeforeWrapping() {
        var current = new PartyIdentifierVersion(Long.MAX_VALUE);
        var failure = assertThrows(DomainValidationException.class, current::next);
        assertEquals(DomainViolation.PARTY_IDENTIFIER_VERSION_OVERFLOW, failure.violation());
        assertEquals(Long.MAX_VALUE, current.value());
    }

    @Test
    void preparingNextAuditAndVersionPreservesOriginalAggregateAndCreationAttribution() {
        var identifier = PartyIdentifierFixtures.identifier(PartyType.NATURAL_PERSON,
                PartyIdentifierStatus.VERIFIED, true, 7, PartyIdentifierFixtures.EVALUATED_ON);
        var protectedValue = identifier.protectedValue();
        var previousAudit = identifier.auditInfo();
        var nextAudit = previousAudit.updated(PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
        var nextVersion = identifier.version().next();
        assertEquals(8, nextVersion.value());
        assertEquals(7, identifier.version().value());
        assertEquals(previousAudit.createdAt(), nextAudit.createdAt());
        assertEquals(previousAudit.createdBy(), nextAudit.createdBy());
        assertEquals(PartyIdentifierFixtures.OCCURRED_AT, nextAudit.updatedAt());
        assertEquals(PartyIdentifierFixtures.ACTOR, nextAudit.updatedBy());
        assertSame(previousAudit, identifier.auditInfo());
        assertSame(protectedValue, identifier.protectedValue());
        assertEquals(PartyIdentifierFixtures.owner(PartyType.NATURAL_PERSON).partyId(), identifier.partyId());
        assertEquals(PartyIdentifierStatus.VERIFIED, identifier.status());
        assertEquals(PartyIdentifierFixtures.CREATED_AT, identifier.verifiedAt());
    }

    @Test
    void existingAuditContractsRejectInvalidActorsAndBackwardInstantsWithoutChangingHistory() {
        var audit = AuditInfo.initial(PartyIdentifierFixtures.CREATED_AT, PartyIdentifierFixtures.CREATOR);
        Instant backward = PartyIdentifierFixtures.CREATED_AT.minusNanos(1);
        var backwardFailure = assertThrows(DomainValidationException.class, () -> audit.updated(backward, PartyIdentifierFixtures.ACTOR));
        assertEquals(DomainViolation.AUDIT_TIMESTAMP_ORDER, backwardFailure.violation());
        for (String actor : List.of("", " ", "unsafe\nactor", "A".repeat(129))) {
            assertThrows(DomainValidationException.class, () -> audit.updated(PartyIdentifierFixtures.OCCURRED_AT, actor));
        }
        assertEquals(PartyIdentifierFixtures.CREATED_AT, audit.updatedAt());
        assertEquals(PartyIdentifierFixtures.CREATOR, audit.updatedBy());
    }
}
