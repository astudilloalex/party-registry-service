package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.testing.PartyIdentifierFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies presence-aware identifier corrections, legal states, coherent UTC dates, and retained identity history. */
class PartyIdentifierMetadataTest {

    @ParameterizedTest
    @EnumSource(value = PartyIdentifierStatus.class, names = {"PENDING_VERIFICATION", "VERIFIED"})
    void clearingIssuerPreservesOmittedDatesAndIndependentIdentityHistory(PartyIdentifierStatus status) {
        var current = fixture(status, 5, PartyIdentifierFixtures.EVALUATED_ON);
        var corrected = current.correctMetadata(FieldUpdate.present(null), FieldUpdate.absent(), FieldUpdate.absent(),
                PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
        assertNull(corrected.issuerCode());
        assertEquals(current.issuedOn(), corrected.issuedOn());
        assertEquals(current.expiresOn(), corrected.expiresOn());
        assertPreserved(current, corrected);
        assertEquals(6, corrected.version().value());
        assertEquals(PartyIdentifierFixtures.OCCURRED_AT, corrected.auditInfo().updatedAt());
        assertEquals(PartyIdentifierFixtures.ACTOR, corrected.auditInfo().updatedBy());
        assertNotSame(current, corrected);
    }

    @ParameterizedTest
    @EnumSource(value = PartyIdentifierStatus.class, names = {"PENDING_VERIFICATION", "VERIFIED"})
    void pastDateEvidenceCanBeRepairedOrClearedButNotByIssuerOnly(PartyIdentifierStatus status) {
        var current = fixture(status, 5, PartyIdentifierFixtures.EVALUATED_ON.minusDays(1));
        for (FieldUpdate<LocalDate> expiration : List.of(FieldUpdate.present(PartyIdentifierFixtures.EVALUATED_ON),
                FieldUpdate.present(PartyIdentifierFixtures.EVALUATED_ON.plusDays(1)), FieldUpdate.<LocalDate>present(null))) {
            var corrected = current.correctMetadata(FieldUpdate.absent(), FieldUpdate.absent(), expiration,
                    PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
            assertEquals(expiration.value(), corrected.expiresOn());
            assertEquals(6, corrected.version().value());
            assertPreserved(current, corrected);
        }
        FieldUpdate<String> changedIssuer = FieldUpdate.present("new authority");
        FieldUpdate<LocalDate> omittedDate = FieldUpdate.absent();
        var failure = assertThrows(DomainValidationException.class,
                () -> current.correctMetadata(changedIssuer, omittedDate, omittedDate,
                        PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR));
        assertEquals(DomainViolation.IDENTIFIER_EXPIRED, failure.violation());
        assertEquals(5, current.version().value());
    }

    @ParameterizedTest
    @EnumSource(value = PartyIdentifierStatus.class, names = {"REJECTED", "REVOKED", "EXPIRED"})
    void terminalStateRejectionPrecedesExhaustionAndDateRepair(PartyIdentifierStatus status) {
        var current = fixture(status, Long.MAX_VALUE, PartyIdentifierFixtures.EVALUATED_ON.minusDays(1));
        FieldUpdate<String> omittedIssuer = FieldUpdate.absent();
        FieldUpdate<LocalDate> omittedIssue = FieldUpdate.absent();
        FieldUpdate<LocalDate> clearedExpiration = FieldUpdate.present(null);
        var failure = assertThrows(DomainValidationException.class,
                () -> current.correctMetadata(omittedIssuer, omittedIssue, clearedExpiration,
                        PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR));
        assertEquals(DomainViolation.PARTY_IDENTIFIER_INVALID_LIFECYCLE, failure.violation());
        assertEquals(Long.MAX_VALUE, current.version().value());
        assertEquals(PartyIdentifierFixtures.EVALUATED_ON.minusDays(1), current.expiresOn());
    }

    @Test
    void eligibleTargetExhaustionPrecedesInvalidResultingDates() {
        var current = fixture(PartyIdentifierStatus.VERIFIED, Long.MAX_VALUE, PartyIdentifierFixtures.EVALUATED_ON);
        var past = FieldUpdate.present(PartyIdentifierFixtures.EVALUATED_ON.minusDays(1));
        FieldUpdate<String> omittedIssuer = FieldUpdate.absent();
        FieldUpdate<LocalDate> omittedIssue = FieldUpdate.absent();
        var failure = assertThrows(DomainValidationException.class,
                () -> current.correctMetadata(omittedIssuer, omittedIssue, past,
                        PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR));
        assertEquals(DomainViolation.PARTY_IDENTIFIER_VERSION_OVERFLOW, failure.violation());
    }

    @Test
    void resultingIntervalIncludesRetainedFieldsAndAllowsFutureIssueWhenExpirationIsCleared() {
        var current = fixture(PartyIdentifierStatus.VERIFIED, 5, PartyIdentifierFixtures.EVALUATED_ON);
        var futureIssue = FieldUpdate.present(PartyIdentifierFixtures.EVALUATED_ON.plusDays(1));
        FieldUpdate<String> omittedIssuer = FieldUpdate.absent();
        FieldUpdate<LocalDate> omittedExpiration = FieldUpdate.absent();
        var failure = assertThrows(DomainValidationException.class,
                () -> current.correctMetadata(omittedIssuer, futureIssue, omittedExpiration,
                        PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR));
        assertEquals(DomainViolation.IDENTIFIER_VALIDITY_DATE_ORDER, failure.violation());
        var corrected = current.correctMetadata(FieldUpdate.absent(), futureIssue, FieldUpdate.present(null),
                PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
        assertEquals(futureIssue.value(), corrected.issuedOn());
        assertNull(corrected.expiresOn());
        assertEquals(5, current.version().value());
    }

    @Test
    void identicalCorrectionsAdvanceOnceAndExactIssuerTextIsNotNormalized() {
        var current = fixture(PartyIdentifierStatus.PENDING_VERIFICATION, 5, PartyIdentifierFixtures.EVALUATED_ON);
        var identical = current.correctMetadata(FieldUpdate.present(current.issuerCode()), FieldUpdate.absent(), FieldUpdate.absent(),
                PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
        assertEquals(6, identical.version().value());
        assertEquals(current.issuerCode(), identical.issuerCode());
        var exactIssuer = "  Mixed authority  ";
        var exact = current.correctMetadata(FieldUpdate.present(exactIssuer), FieldUpdate.absent(), FieldUpdate.absent(),
                PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
        assertEquals(exactIssuer, exact.issuerCode());
        assertEquals(6, exact.version().value());
    }

    @Test
    void correctionClearsIssueIndependentlyAndUsesInclusiveUtcEvaluationDate() {
        var current = fixture(PartyIdentifierStatus.VERIFIED, 5, PartyIdentifierFixtures.EVALUATED_ON);
        var corrected = current.correctMetadata(FieldUpdate.absent(), FieldUpdate.present(null), FieldUpdate.absent(),
                PartyIdentifierFixtures.EVALUATED_ON.atTime(23, 59, 59).toInstant(java.time.ZoneOffset.UTC), PartyIdentifierFixtures.ACTOR);
        assertNull(corrected.issuedOn());
        assertEquals(current.expiresOn(), corrected.expiresOn());
        assertTrue(corrected.isPrimary());
    }

    private static PartyIdentifier fixture(PartyIdentifierStatus status, long version, LocalDate expiration) {
        return PartyIdentifierFixtures.identifier(PartyType.NATURAL_PERSON, status, true, version, expiration);
    }

    private static void assertPreserved(PartyIdentifier current, PartyIdentifier corrected) {
        assertEquals(current.identifierId(), corrected.identifierId());
        assertEquals(current.tenantId(), corrected.tenantId());
        assertEquals(current.partyId(), corrected.partyId());
        assertEquals(current.identifierSchemeId(), corrected.identifierSchemeId());
        assertSame(current.protectedValue(), corrected.protectedValue());
        assertEquals(current.status(), corrected.status());
        assertEquals(current.isPrimary(), corrected.isPrimary());
        assertEquals(current.verifiedAt(), corrected.verifiedAt());
        assertEquals(current.verifiedBy(), corrected.verifiedBy());
        assertEquals(current.auditInfo().createdAt(), corrected.auditInfo().createdAt());
        assertEquals(current.auditInfo().createdBy(), corrected.auditInfo().createdBy());
    }
}
