package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies legal-entity aggregate creation and type-specific invariants.
 */
class LegalEntityTest {

    private static final PartyId PARTY_ID = new PartyId(
            UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
    private static final TenantId TENANT_ID = new TenantId(
            UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));
    private static final LocalDate EVALUATED_ON = LocalDate.of(2026, 8, 30);
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T10:00:00Z");

    @Test
    void createsLegalEntityWithImmutableTypeAndInitialState() {
        LegalEntityDetails details = details(
                "Analytical Engines Ltd.",
                "Analytical Engines",
                "LTD",
                "GB",
                LocalDate.of(2020, 1, 15),
                null);

        LegalEntity entity = LegalEntity.create(
                PARTY_ID,
                TENANT_ID,
                "Analytical Engines",
                details,
                EVALUATED_ON,
                CREATED_AT,
                "creator");

        assertEquals(PartyType.LEGAL_ENTITY, entity.type());
        assertEquals(PartyRecordStatus.DRAFT, entity.recordStatus());
        assertEquals(PartyVersion.initial(), entity.version());
        assertEquals(details, entity.details());
        assertEquals(AuditInfo.initial(CREATED_AT, "creator"), entity.auditInfo());
        assertFalse(Arrays.stream(LegalEntity.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == NaturalPersonDetails.class));
    }

    @Test
    void derivesDisplayNameFromTrimmedLegalName() {
        LegalEntity entity = LegalEntity.create(
                PARTY_ID,
                TENANT_ID,
                null,
                details("  Analytical Engines Ltd.  ", null, null, "GB", null, null),
                EVALUATED_ON,
                CREATED_AT,
                "creator");

        assertEquals("Analytical Engines Ltd.", entity.displayName());
        assertNull(entity.details().tradeName());
    }

    @Test
    void acceptsInclusiveTextBoundaries() {
        LegalEntity entity = LegalEntity.create(
                PARTY_ID,
                TENANT_ID,
                "D".repeat(300),
                details("L".repeat(300), "T".repeat(300), "F".repeat(64), "GB", null, null),
                EVALUATED_ON,
                CREATED_AT,
                "creator");

        assertEquals(300, entity.displayName().length());
        assertEquals(300, entity.details().legalName().length());
        assertEquals(64, entity.details().legalFormCode().length());
    }

    @Test
    void rejectsMissingOrOversizedLegalName() {
        assertViolation(DomainViolation.LEGAL_NAME_REQUIRED,
                () -> details(null, null, null, "GB", null, null));
        assertViolation(DomainViolation.LEGAL_NAME_REQUIRED,
                () -> details("  ", null, null, "GB", null, null));
        assertViolation(DomainViolation.LEGAL_NAME_TOO_LONG,
                () -> details("L".repeat(301), null, null, "GB", null, null));
    }

    @Test
    void rejectsOversizedOptionalNamesAndCodes() {
        assertViolation(DomainViolation.TRADE_NAME_TOO_LONG,
                () -> details("Legal", "T".repeat(301), null, "GB", null, null));
        assertViolation(DomainViolation.LEGAL_FORM_CODE_TOO_LONG,
                () -> details("Legal", null, "F".repeat(65), "GB", null, null));
    }

    @Test
    void rejectsMissingOrInvalidIncorporationCountry() {
        assertViolation(DomainViolation.INCORPORATION_COUNTRY_CODE_REQUIRED,
                () -> details("Legal", null, null, null, null, null));
        assertViolation(DomainViolation.INCORPORATION_COUNTRY_CODE_REQUIRED,
                () -> details("Legal", null, null, "  ", null, null));
        assertViolation(DomainViolation.INCORPORATION_COUNTRY_CODE_INVALID,
                () -> details("Legal", null, null, "gb", null, null));
        assertViolation(DomainViolation.INCORPORATION_COUNTRY_CODE_INVALID,
                () -> details("Legal", null, null, "GBR", null, null));
    }

    @Test
    void rejectsIncoherentOrFutureLifecycleDates() {
        assertViolation(DomainViolation.DISSOLUTION_BEFORE_INCORPORATION,
                () -> details(
                        "Legal",
                        null,
                        null,
                        "GB",
                        LocalDate.of(2020, 1, 2),
                        LocalDate.of(2020, 1, 1)));

        LegalEntityDetails futureIncorporation = details(
                "Legal", null, null, "GB", EVALUATED_ON.plusDays(1), null);
        LegalEntityDetails futureDissolution = details(
                "Legal", null, null, "GB", null, EVALUATED_ON.plusDays(1));
        assertViolation(DomainViolation.INCORPORATION_DATE_IN_FUTURE,
                () -> create(futureIncorporation));
        assertViolation(DomainViolation.DISSOLUTION_DATE_IN_FUTURE,
                () -> create(futureDissolution));
        assertViolation(DomainViolation.EVALUATION_DATE_REQUIRED,
                () -> futureIncorporation.validateAt(null));
    }

    @Test
    void acceptsEqualIncorporationAndDissolutionDates() {
        LocalDate date = LocalDate.of(2020, 1, 1);

        LegalEntity entity = create(details("Legal", null, null, "GB", date, date));

        assertEquals(date, entity.details().incorporatedOn());
        assertEquals(date, entity.details().dissolvedOn());
    }

    @Test
    void rejectsMissingDetailsAndInvalidDisplayName() {
        assertViolation(DomainViolation.LEGAL_ENTITY_DETAILS_REQUIRED, () -> create(null));
        assertViolation(DomainViolation.DISPLAY_NAME_REQUIRED,
                () -> LegalEntity.create(
                        PARTY_ID,
                        TENANT_ID,
                        "  ",
                        details("Legal", null, null, "GB", null, null),
                        EVALUATED_ON,
                        CREATED_AT,
                        "creator"));
        assertViolation(DomainViolation.DISPLAY_NAME_TOO_LONG,
                () -> LegalEntity.create(
                        PARTY_ID,
                        TENANT_ID,
                        "D".repeat(301),
                        details("Legal", null, null, "GB", null, null),
                        EVALUATED_ON,
                        CREATED_AT,
                        "creator"));
    }

    @Test
    void rejectsMissingAggregateIdentityLifecycleAndAuditState() {
        LegalEntityDetails details = details("Legal", null, null, "GB", null, null);
        AuditInfo audit = AuditInfo.initial(CREATED_AT, "creator");

        assertViolation(DomainViolation.PARTY_ID_REQUIRED,
                () -> LegalEntity.create(
                        null, TENANT_ID, null, details, EVALUATED_ON, CREATED_AT, "creator"));
        assertViolation(DomainViolation.TENANT_ID_REQUIRED,
                () -> LegalEntity.create(
                        PARTY_ID, null, null, details, EVALUATED_ON, CREATED_AT, "creator"));
        assertViolation(DomainViolation.AUDIT_TIMESTAMP_REQUIRED,
                () -> LegalEntity.create(
                        PARTY_ID, TENANT_ID, null, details, EVALUATED_ON, null, "creator"));
        assertViolation(DomainViolation.AUDIT_USER_REQUIRED,
                () -> LegalEntity.create(
                        PARTY_ID, TENANT_ID, null, details, EVALUATED_ON, CREATED_AT, " "));
        assertViolation(DomainViolation.PARTY_STATUS_REQUIRED,
                () -> LegalEntity.restore(
                        PARTY_ID, TENANT_ID, "Legal", null, PartyVersion.initial(), audit, details));
        assertViolation(DomainViolation.PARTY_VERSION_REQUIRED,
                () -> LegalEntity.restore(
                        PARTY_ID, TENANT_ID, "Legal", PartyRecordStatus.DRAFT, null, audit, details));
        assertViolation(DomainViolation.AUDIT_REQUIRED,
                () -> LegalEntity.restore(
                        PARTY_ID,
                        TENANT_ID,
                        "Legal",
                        PartyRecordStatus.DRAFT,
                        PartyVersion.initial(),
                        null,
                        details));
    }

    @Test
    void restoresPersistedLegalEntityState() {
        LegalEntityDetails details = details("Legal", null, null, "GB", null, null);
        AuditInfo audit = AuditInfo.initial(CREATED_AT, "creator");

        LegalEntity restored = LegalEntity.restore(
                PARTY_ID,
                TENANT_ID,
                "Legal",
                PartyRecordStatus.ACTIVE,
                new PartyVersion(4),
                audit,
                details);

        assertEquals(PartyType.LEGAL_ENTITY, restored.type());
        assertEquals(PartyRecordStatus.ACTIVE, restored.recordStatus());
        assertEquals(new PartyVersion(4), restored.version());
        assertEquals(details, restored.details());
    }

    private static LegalEntity create(LegalEntityDetails details) {
        return LegalEntity.create(
                PARTY_ID,
                TENANT_ID,
                null,
                details,
                EVALUATED_ON,
                CREATED_AT,
                "creator");
    }

    private static LegalEntityDetails details(
            String legalName,
            String tradeName,
            String legalFormCode,
            String countryCode,
            LocalDate incorporatedOn,
            LocalDate dissolvedOn) {
        return new LegalEntityDetails(
                legalName,
                tradeName,
                legalFormCode,
                countryCode,
                incorporatedOn,
                dissolvedOn);
    }

    private static void assertViolation(DomainViolation violation, Runnable action) {
        DomainValidationException failure = assertThrows(DomainValidationException.class, action::run);
        assertEquals(violation, failure.violation());
    }
}
