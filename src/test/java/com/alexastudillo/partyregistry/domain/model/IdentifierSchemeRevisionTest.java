package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies one immutable version/audit advance per accepted correction and safe exhaustion handling. */
class IdentifierSchemeRevisionTest {

    private static final Instant CREATED = Instant.parse("2026-09-30T00:00:00Z");
    private static final Instant CHANGED = Instant.parse("2026-09-30T00:01:00Z");

    @Test
    void advancesAnOrdinaryVersionWithoutChangingTheOriginal() {
        var initial = IdentifierSchemeVersion.initial();
        assertEquals(new IdentifierSchemeVersion(1), initial.next());
        assertEquals(new IdentifierSchemeVersion(0), initial);
        assertEquals(new IdentifierSchemeVersion(Long.MAX_VALUE), new IdentifierSchemeVersion(Long.MAX_VALUE - 1).next());
    }

    @Test
    void preventsVersionOverflow() {
        var exhausted = new IdentifierSchemeVersion(Long.MAX_VALUE);
        assertEquals(DomainViolation.IDENTIFIER_SCHEME_VERSION_OVERFLOW,
                assertThrows(DomainValidationException.class, exhausted::next).violation());
        assertEquals(Long.MAX_VALUE, exhausted.value());
    }

    @Test
    void identicalNameCorrectionStillAdvancesOneVersionAndRetainsCreationAudit() {
        var original = scheme(IdentifierSchemeStatus.ACTIVE, 4);
        var changed = original.applyChanges(name("Name"), CHANGED, "editor");
        assertEquals(5, changed.version().value());
        assertEquals(IdentifierSchemeStatus.ACTIVE, changed.status());
        assertEquals("Name", changed.name());
        assertEquals(new AuditInfo(CREATED, "creator", CHANGED, "editor"), changed.auditInfo());
        assertEquals(original.id(), changed.id());
        assertEquals(original.code(), changed.code());
        assertEquals(original.issuingCountryCode(), changed.issuingCountryCode());
        assertEquals(original.category(), changed.category());
        assertEquals(original.applicableSubjectType(), changed.applicableSubjectType());
        assertEquals(scheme(IdentifierSchemeStatus.ACTIVE, 4), original);
    }

    @Test
    void stateRestrictionPrecedesExhaustionAndExhaustionPrecedesCandidateValidation() {
        var retired = scheme(IdentifierSchemeStatus.RETIRED, Long.MAX_VALUE);
        var validChanges = name("Name");
        assertEquals(DomainViolation.IDENTIFIER_SCHEME_RETIRED, assertThrows(DomainValidationException.class,
                () -> retired.applyChanges(validChanges, CHANGED, "editor"))
                .violation());
        var exhausted = scheme(IdentifierSchemeStatus.DRAFT, Long.MAX_VALUE);
        var nullNameChanges = name(null);
        assertEquals(DomainViolation.IDENTIFIER_SCHEME_VERSION_OVERFLOW, assertThrows(DomainValidationException.class,
                () -> exhausted.applyChanges(nullNameChanges, CHANGED, "editor")).violation());
        assertEquals(scheme(IdentifierSchemeStatus.DRAFT, Long.MAX_VALUE), exhausted);
    }

    @Test
    void failedCandidateOrAuditDoesNotChangeTheOriginal() {
        var current = scheme(IdentifierSchemeStatus.DRAFT, 4);
        var nullNameChanges = name(null);
        assertEquals(DomainViolation.IDENTIFIER_SCHEME_NAME_REQUIRED, assertThrows(DomainValidationException.class,
                () -> current.applyChanges(nullNameChanges, CHANGED, "editor")).violation());
        var newNameChanges = name("New name");
        var invalidTimestamp = CREATED.minusSeconds(1);
        assertEquals(DomainViolation.AUDIT_TIMESTAMP_ORDER, assertThrows(DomainValidationException.class,
                () -> current.applyChanges(newNameChanges, invalidTimestamp, "editor")).violation());
        assertEquals(scheme(IdentifierSchemeStatus.DRAFT, 4), current);
    }

    private static IdentifierSchemeChanges name(String value) {
        return new IdentifierSchemeChanges(FieldUpdate.present(value), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent());
    }

    private static IdentifierScheme scheme(IdentifierSchemeStatus status, long version) {
        return new IdentifierScheme(new IdentifierSchemeId(UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd996")),
                "CODE", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, "Name", null,
                "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", null, null, false, status,
                new IdentifierSchemeVersion(version), AuditInfo.initial(CREATED, "creator"));
    }
}
