package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies complete and partial legal-detail changes without rewriting
 * historical state.
 */
class LegalEntityDetailsUpdateTest {

    private static final LocalDate TODAY = LocalDate.of(2026, Month.SEPTEMBER, 13);
    private static final Instant CREATED = Instant.parse("2026-09-01T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-09-13T10:00:00Z");

    @Test
    void replacementCanonicalizesDetailsAndPreservesIdentityLifecycleAndCreationAudit() {
        LegalEntity original = historical();
        LegalEntity updated = replace(original, LegalEntityDetails.forWrite(
                "  Compañía Águila S.A.  ", "  New  Brand  ", " sa ", " gb ", TODAY, TODAY));

        assertEquals("COMPAÑÍA ÁGUILA S.A.", updated.details().legalName());
        assertEquals("COMPAÑÍA ÁGUILA S.A.", updated.displayName());
        assertEquals("NEW  BRAND", updated.details().tradeName());
        assertEquals("SA", updated.details().legalFormCode());
        assertEquals("GB", updated.details().incorporationCountryCode());
        assertEquals(TODAY, updated.details().incorporatedOn());
        assertEquals(TODAY, updated.details().dissolvedOn());
        assertPreservedRootAndUpdatedAudit(original, updated);
        assertEquals(" Example Company ", original.details().legalName());
        assertEquals("Custom Label", original.displayName());
    }

    @Test
    void replacementClearsAllOptionalFields() {
        LegalEntity original = historical();
        LegalEntity updated = replace(original, LegalEntityDetails.forWrite(
                "Example Company", null, null, "EC", null, null));

        assertNull(updated.details().tradeName());
        assertNull(updated.details().legalFormCode());
        assertNull(updated.details().incorporatedOn());
        assertNull(updated.details().dissolvedOn());
        assertEquals("Custom Label", updated.displayName());
        assertPreservedRootAndUpdatedAudit(original, updated);
    }

    @Test
    void patchNormalizesOnlyPresentText() {
        LegalEntity original = historical();
        LegalEntity updated = patch(original, new LegalEntityPatch(
                FieldUpdate.absent(), FieldUpdate.present("  New Brand  "), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent()));

        assertEquals("NEW BRAND", updated.details().tradeName());
        assertEquals(original.details().legalName(), updated.details().legalName());
        assertEquals(original.details().legalFormCode(), updated.details().legalFormCode());
        assertEquals(original.details().incorporatedOn(), updated.details().incorporatedOn());
        assertEquals("Custom Label", updated.displayName());
        assertPreservedRootAndUpdatedAudit(original, updated);
    }

    @Test
    void explicitNullClearsOnlyTheSelectedOptionalValue() {
        LegalEntity original = historical();
        LegalEntity updated = patch(original, new LegalEntityPatch(
                FieldUpdate.absent(), FieldUpdate.present(null), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent()));

        assertNull(updated.details().tradeName());
        assertEquals(original.details().legalFormCode(), updated.details().legalFormCode());
        assertEquals(original.details().incorporatedOn(), updated.details().incorporatedOn());
        assertEquals(original.details().dissolvedOn(), updated.details().dissolvedOn());
        LegalEntity cleared = patch(original, new LegalEntityPatch(
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.present(null),
                FieldUpdate.absent(), FieldUpdate.present(null), FieldUpdate.present(null)));
        assertNull(cleared.details().legalFormCode());
        assertNull(cleared.details().incorporatedOn());
        assertNull(cleared.details().dissolvedOn());
        assertEquals(original.details().tradeName(), cleared.details().tradeName());
    }

    @Test
    void canonicalNameEquivalencePreservesCustomDisplayNameForBothUpdates() {
        LegalEntity original = historical();
        LegalEntity patched = patch(original, namePatch("  example company  "));
        LegalEntity replaced = replace(original, LegalEntityDetails.forWrite(
                "  example company  ", null, null, "EC", null, null));
        for (LegalEntity result : List.of(patched, replaced)) {
            assertEquals("EXAMPLE COMPANY", result.details().legalName());
            assertEquals("Custom Label", result.displayName());
        }
        assertEquals("NEW NAME", patch(original, namePatch("new name")).displayName());
    }

    @Test
    void rejectsMissingReplacementEmptyPatchAndMandatoryNull() {
        LegalEntity original = historical();
        assertViolation(DomainViolation.LEGAL_ENTITY_DETAILS_REQUIRED, () -> replace(original, null));
        assertViolation(DomainViolation.PATCH_REQUIRED, () -> patch(original, null));
        assertViolation(DomainViolation.EMPTY_PATCH, () -> patch(original, LegalEntityPatch.empty()));
        assertViolation(DomainViolation.LEGAL_NAME_REQUIRED, () -> patch(original, namePatch(null)));
        assertViolation(DomainViolation.LEGAL_NAME_REQUIRED, () -> patch(original, namePatch("  ")));
        assertViolation(DomainViolation.INCORPORATION_COUNTRY_CODE_REQUIRED,
                () -> patch(original, countryPatch(null)));
    }

    @Test
    void rejectsInvalidCountriesAndCanonicalizesAsciiInput() {
        LegalEntity original = historical();
        for (String invalid : List.of("E", "ECU", "E C", "ß", "ıS", "ＥＣ")) {
            assertViolation(DomainViolation.INCORPORATION_COUNTRY_CODE_INVALID,
                    () -> patch(original, countryPatch(invalid)));
        }
        assertEquals("GB", patch(original, countryPatch("\u2003gb\u3000"))
                .details().incorporationCountryCode());
    }

    @Test
    void validatesMergedDatesAndRetainsSpecificViolations() {
        LegalEntity original = historical();
        assertViolation(DomainViolation.DISSOLUTION_BEFORE_INCORPORATION,
                () -> patch(original, new LegalEntityPatch(
                        FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(),
                        FieldUpdate.absent(), FieldUpdate.absent(),
                        FieldUpdate.present(LocalDate.of(2019, Month.JANUARY, 1)))));
        assertViolation(DomainViolation.DISSOLUTION_DATE_IN_FUTURE,
                () -> patch(original, new LegalEntityPatch(
                        FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(),
                        FieldUpdate.absent(), FieldUpdate.absent(),
                        FieldUpdate.present(TODAY.plusDays(1)))));
        assertViolation(DomainViolation.INCORPORATION_DATE_IN_FUTURE,
                () -> replace(original, LegalEntityDetails.forWrite(
                        "Legal", null, null, "EC", TODAY.plusDays(1), TODAY.plusDays(1))));
        assertViolation(DomainViolation.DISSOLUTION_BEFORE_INCORPORATION,
                () -> patch(original, new LegalEntityPatch(
                        FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(),
                        FieldUpdate.absent(),
                        FieldUpdate.present(TODAY.plusDays(2)),
                        FieldUpdate.present(TODAY.plusDays(1)))));
        assertEquals(historical().details(), original.details());
        assertEquals(AuditInfo.initial(CREATED, "creator"), original.auditInfo());
    }

    @Test
    void acceptsDatesOnTheEvaluationBoundaryAndRejectsMissingEvaluationDate() {
        LegalEntity original = historical();
        LegalEntityPatch dates = new LegalEntityPatch(
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.present(TODAY), FieldUpdate.present(TODAY));
        LegalEntity updated = patch(original, dates);
        assertEquals(TODAY, updated.details().incorporatedOn());
        assertEquals(TODAY, updated.details().dissolvedOn());
        assertViolation(DomainViolation.EVALUATION_DATE_REQUIRED,
                () -> original.patchDetails(dates, null, UPDATED, "editor"));
    }

    @Test
    void enforcesNormalizedUtf16LimitsForNewNamesAndCodes() {
        LegalEntity original = historical();
        for (String name : List.of("L".repeat(301), "ß".repeat(151), "😀".repeat(151))) {
            assertViolation(DomainViolation.LEGAL_NAME_TOO_LONG, () -> patch(original, namePatch(name)));
            assertViolation(DomainViolation.LEGAL_NAME_TOO_LONG, () -> replace(original,
                    LegalEntityDetails.forWrite(name, null, null, "EC", null, null)));
            assertViolation(DomainViolation.TRADE_NAME_TOO_LONG, () -> patch(original, new LegalEntityPatch(
                    FieldUpdate.absent(), FieldUpdate.present(name), FieldUpdate.absent(),
                    FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent())));
        }
        for (String code : List.of("F".repeat(65), "ß".repeat(33), "😀".repeat(33))) {
            assertViolation(DomainViolation.LEGAL_FORM_CODE_TOO_LONG,
                    () -> patch(original, new LegalEntityPatch(
                            FieldUpdate.absent(), FieldUpdate.absent(),
                            FieldUpdate.present(code),
                            FieldUpdate.absent(), FieldUpdate.absent(),
                            FieldUpdate.absent())));
        }
        LegalEntity accepted = replace(original, LegalEntityDetails.forWrite(
                "  " + "ß".repeat(150) + "  ", "😀".repeat(150), "ß".repeat(32), "EC", null, null));
        assertEquals(300, accepted.details().legalName().length());
        assertEquals(300, accepted.details().tradeName().length());
        assertEquals(64, accepted.details().legalFormCode().length());
    }

    @Test
    void newWriteLimitsDoNotRejectUntouchedHistoricalUnicode() {
        LegalEntity base = historical();
        LegalEntity historical = LegalEntity.restore(base.partyId(), base.tenantId(), base.displayName(),
                base.recordStatus(), base.version(), base.auditInfo(),
                new LegalEntityDetails("😀".repeat(200), "Historical", "Ltd", "EC", null, null));
        LegalEntity updated = patch(historical, new LegalEntityPatch(
                FieldUpdate.absent(), FieldUpdate.present("Changed"), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent()));
        assertEquals(historical.details().legalName(), updated.details().legalName());
        assertEquals(historical.displayName(), updated.displayName());
        assertEquals("CHANGED", updated.details().tradeName());
    }

    @Test
    void identicalCandidateRetainsVersionAndUpdatesAuditForPersistence() {
        LegalEntity original = historical();
        LegalEntity first = replace(original, LegalEntityDetails.forWrite(
                "Example Company", "Old Brand", "Ltd", "EC", null, null));
        LegalEntity identical = first.replaceDetails(first.details(), TODAY, UPDATED.plusSeconds(1),
                "next-editor");
        assertEquals(first.details(), identical.details());
        assertEquals(first.version(), identical.version());
        assertEquals(UPDATED.plusSeconds(1), identical.auditInfo().updatedAt());
        assertEquals("next-editor", identical.auditInfo().updatedBy());
        assertViolation(DomainViolation.AUDIT_TIMESTAMP_ORDER,
                () -> first.replaceDetails(first.details(), TODAY, CREATED, "editor"));
    }

    private static LegalEntity historical() {
        return LegalEntity.restore(
                new PartyId(UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1")),
                new TenantId(UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5")),
                "Custom Label", PartyRecordStatus.ACTIVE, new PartyVersion(4),
                AuditInfo.initial(CREATED, "creator"), new LegalEntityDetails(
                        " Example Company ", " Old Brand ", " Ltd ", "EC",
                        LocalDate.of(2020, Month.JANUARY, 15), LocalDate.of(2025, Month.JANUARY, 15)));
    }

    private static LegalEntity replace(LegalEntity entity, LegalEntityDetails details) {
        return entity.replaceDetails(details, TODAY, UPDATED, "editor");
    }

    private static LegalEntity patch(LegalEntity entity, LegalEntityPatch patch) {
        return entity.patchDetails(patch, TODAY, UPDATED, "editor");
    }

    private static LegalEntityPatch namePatch(String value) {
        return new LegalEntityPatch(FieldUpdate.present(value), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent());
    }

    private static LegalEntityPatch countryPatch(String value) {
        return new LegalEntityPatch(FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.present(value), FieldUpdate.absent(), FieldUpdate.absent());
    }

    private static void assertPreservedRootAndUpdatedAudit(LegalEntity original, LegalEntity updated) {
        assertEquals(original.partyId(), updated.partyId());
        assertEquals(original.tenantId(), updated.tenantId());
        assertEquals(original.type(), updated.type());
        assertEquals(original.recordStatus(), updated.recordStatus());
        assertEquals(original.version(), updated.version());
        assertEquals(new AuditInfo(CREATED, "creator", UPDATED, "editor"), updated.auditInfo());
    }

    private static void assertViolation(DomainViolation violation, Runnable action) {
        assertEquals(violation, assertThrows(DomainValidationException.class, action::run).violation());
    }
}
