package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies natural-person aggregate invariants and immutable state transitions.
 */
class NaturalPersonTest {

    private static final LocalDate EVALUATED_ON = LocalDate.of(2026, Month.AUGUST, 30);
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-30T11:00:00Z");
    private static final String CREATED_BY = "creator";
    private static final String UPDATED_BY = "updater";

    @Test
    void createsNaturalPersonWithExplicitDisplayNameAndInitialState() {
        NaturalPerson person = createPerson(
                "Ada L.",
                details("Ada", "Lovelace", "Ada", LocalDate.of(1815, Month.DECEMBER, 10), null, "GB"));

        assertEquals(PartyType.NATURAL_PERSON, person.type());
        assertEquals("Ada L.", person.displayName());
        assertEquals(PartyRecordStatus.DRAFT, person.recordStatus());
        assertEquals(PartyVersion.initial(), person.version());
        assertEquals(CREATED_AT, person.auditInfo().createdAt());
        assertEquals(CREATED_AT, person.auditInfo().updatedAt());
        assertEquals(CREATED_BY, person.auditInfo().createdBy());
        assertEquals(CREATED_BY, person.auditInfo().updatedBy());
    }

    @Test
    void derivesDisplayNameFromTrimmedRequiredNames() {
        NaturalPerson person = createPerson(
                null,
                details("  Ada ", " Lovelace  ", null, null, null, null));

        assertEquals("Ada Lovelace", person.displayName());
        assertEquals("  Ada ", person.details().givenNames());
        assertEquals(" Lovelace  ", person.details().familyNames());
    }

    @Test
    void rejectsBlankRequiredNames() {
        assertViolation(
                DomainViolation.GIVEN_NAMES_REQUIRED,
                () -> details("   ", "Lovelace", null, null, null, null));
        assertViolation(
                DomainViolation.FAMILY_NAMES_REQUIRED,
                () -> details("Ada", "   ", null, null, null, null));
    }

    @Test
    void appliesTextLimitsByUnicodeCodePoint() {
        String twoHundredSupplementaryCharacters = "😀".repeat(200);

        NaturalPerson person = createPerson(
                "😀".repeat(300),
                details(
                        twoHundredSupplementaryCharacters,
                        twoHundredSupplementaryCharacters,
                        twoHundredSupplementaryCharacters,
                        null,
                        null,
                        null));

        assertEquals(300, person.displayName().codePointCount(0, person.displayName().length()));
        assertViolation(
                DomainViolation.GIVEN_NAMES_TOO_LONG,
                () -> details("😀".repeat(201), "Family", null, null, null, null));
        assertViolation(
                DomainViolation.DISPLAY_NAME_TOO_LONG,
                () -> createPerson(
                        "😀".repeat(301),
                        details("Given", "Family", null, null, null, null)));
    }

    @Test
    void rejectsMissingNaturalPersonDetails() {
        assertViolation(
                DomainViolation.NATURAL_PERSON_DETAILS_REQUIRED,
                () -> createPerson(null, null));
    }

    @Test
    void rejectsFutureBirthAndDeathDates() {
        NaturalPersonDetails futureBirth = details(
                "Ada", "Lovelace", null, EVALUATED_ON.plusDays(1), null, null);
        NaturalPersonDetails futureDeath = details(
                "Ada", "Lovelace", null, null, EVALUATED_ON.plusDays(1), null);

        assertViolation(
                DomainViolation.BIRTH_DATE_IN_FUTURE,
                () -> createPerson(null, futureBirth));
        assertViolation(
                DomainViolation.DATE_OF_DEATH_IN_FUTURE,
                () -> createPerson(null, futureDeath));
    }

    @Test
    void rejectsDeathBeforeBirth() {
        assertViolation(
                DomainViolation.DEATH_BEFORE_BIRTH,
                () -> details(
                        "Ada",
                        "Lovelace",
                        null,
                        LocalDate.of(1815, Month.DECEMBER, 10),
                        LocalDate.of(1815, Month.DECEMBER, 9),
                        null));
    }

    @Test
    void acceptsSameDayBirthAndDeath() {
        LocalDate date = LocalDate.of(2000, Month.JANUARY, 1);

        NaturalPerson person = createPerson(
                null,
                details("Same", "Day", null, date, date, null));

        assertEquals(date, person.details().birthDate());
        assertEquals(date, person.details().dateOfDeath());
    }

    @Test
    void completeReplacementClearsOptionalFields() {
        NaturalPerson original = createPerson(
                "Ada L.",
                details(
                        "Ada",
                        "Lovelace",
                        "Ada",
                        LocalDate.of(1815, Month.DECEMBER, 10),
                        LocalDate.of(1852, Month.NOVEMBER, 27),
                        "GB"));
        NaturalPersonDetails replacement = details("Ada", "Lovelace", null, null, null, null);

        NaturalPerson replaced = original.replaceDetails(
                replacement,
                EVALUATED_ON,
                UPDATED_AT,
                UPDATED_BY);

        assertEquals(replacement, replaced.details());
        assertNull(replaced.details().preferredName());
        assertNull(replaced.details().birthDate());
        assertNull(replaced.details().dateOfDeath());
        assertNull(replaced.details().birthCountryCode());
        assertEquals("Ada L.", replaced.displayName());
        assertEquals(original.version(), replaced.version());
        assertEquals(UPDATED_BY, replaced.auditInfo().updatedBy());
    }

    @Test
    void completeReplacementRederivesDisplayNameWhenNamesChange() {
        NaturalPerson original = createPerson(
                "Ada L.",
                details("Ada", "Lovelace", null, null, null, null));

        NaturalPerson replaced = original.replaceDetails(
                details("Augusta Ada", "King", null, null, null, null),
                EVALUATED_ON,
                UPDATED_AT,
                UPDATED_BY);

        assertEquals("Augusta Ada King", replaced.displayName());
    }

    @Test
    void failedReplacementPreservesOriginalAggregate() {
        NaturalPerson original = createPerson(
                "Ada L.",
                details("Ada", "Lovelace", "Ada", LocalDate.of(1815, Month.DECEMBER, 10), null, "GB"));
        NaturalPersonDetails futureReplacement = details(
                "Future",
                "Person",
                null,
                EVALUATED_ON.plusDays(1),
                null,
                null);

        assertViolation(
                DomainViolation.BIRTH_DATE_IN_FUTURE,
                () -> original.replaceDetails(
                        futureReplacement,
                        EVALUATED_ON,
                        UPDATED_AT,
                        UPDATED_BY));
        assertEquals("Ada L.", original.displayName());
        assertEquals("Ada", original.details().preferredName());
        assertEquals(PartyVersion.initial(), original.version());
        assertEquals(CREATED_AT, original.auditInfo().updatedAt());
    }

    @Test
    void patchUpdatesOnlyPresentFields() {
        NaturalPerson original = createPerson(
                "Ada L.",
                details("Ada", "Lovelace", "Ada", LocalDate.of(1815, Month.DECEMBER, 10), null, "GB"));
        NaturalPersonPatch patch = patch(
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.present("Countess"),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent());

        NaturalPerson updated = original.patchDetails(patch, EVALUATED_ON, UPDATED_AT, UPDATED_BY);

        assertEquals("Countess", updated.details().preferredName());
        assertEquals(original.details().givenNames(), updated.details().givenNames());
        assertEquals(original.details().familyNames(), updated.details().familyNames());
        assertEquals(original.details().birthDate(), updated.details().birthDate());
        assertEquals(original.details().birthCountryCode(), updated.details().birthCountryCode());
        assertEquals(original.displayName(), updated.displayName());
        assertNotSame(original, updated);
    }

    @Test
    void patchExplicitNullClearsNullableFields() {
        NaturalPerson original = createPerson(
                null,
                details(
                        "Ada",
                        "Lovelace",
                        "Ada",
                        LocalDate.of(1815, Month.DECEMBER, 10),
                        LocalDate.of(1852, Month.NOVEMBER, 27),
                        "GB"));
        NaturalPersonPatch patch = patch(
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.present(null),
                FieldUpdate.present(null),
                FieldUpdate.present(null),
                FieldUpdate.present(null));

        NaturalPerson updated = original.patchDetails(patch, EVALUATED_ON, UPDATED_AT, UPDATED_BY);

        assertNull(updated.details().preferredName());
        assertNull(updated.details().birthDate());
        assertNull(updated.details().birthCountryCode());
        assertNull(updated.details().dateOfDeath());
    }

    @Test
    void patchRejectsClearedOrBlankRequiredNames() {
        NaturalPerson original = createPerson(
                null,
                details("Ada", "Lovelace", null, null, null, null));
        NaturalPersonPatch clearGivenNames = patch(
                FieldUpdate.present(null),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent());
        NaturalPersonPatch blankFamilyNames = patch(
                FieldUpdate.absent(),
                FieldUpdate.present("  "),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent());

        assertViolation(
                DomainViolation.GIVEN_NAMES_REQUIRED,
                () -> original.patchDetails(clearGivenNames, EVALUATED_ON, UPDATED_AT, UPDATED_BY));
        assertViolation(
                DomainViolation.FAMILY_NAMES_REQUIRED,
                () -> original.patchDetails(blankFamilyNames, EVALUATED_ON, UPDATED_AT, UPDATED_BY));
    }

    @Test
    void patchRejectsEmptyUpdate() {
        NaturalPerson original = createPerson(
                null,
                details("Ada", "Lovelace", null, null, null, null));

        assertViolation(
                DomainViolation.EMPTY_PATCH,
                () -> original.patchDetails(
                        NaturalPersonPatch.empty(),
                        EVALUATED_ON,
                        UPDATED_AT,
                        UPDATED_BY));
    }

    @Test
    void patchValidatesChangedDateAgainstRetainedDate() {
        NaturalPerson original = createPerson(
                null,
                details("Ada", "Lovelace", null, LocalDate.of(1815, Month.DECEMBER, 10), null, null));
        NaturalPersonPatch patch = patch(
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.present(LocalDate.of(1815, Month.DECEMBER, 9)),
                FieldUpdate.absent());

        assertViolation(
                DomainViolation.DEATH_BEFORE_BIRTH,
                () -> original.patchDetails(patch, EVALUATED_ON, UPDATED_AT, UPDATED_BY));
        assertNull(original.details().dateOfDeath());
        assertEquals(PartyVersion.initial(), original.version());
    }

    @Test
    void patchRederivesDisplayNameWhenRequiredNameChanges() {
        NaturalPerson original = createPerson(
                "Ada L.",
                details("Ada", "Lovelace", null, null, null, null));
        NaturalPersonPatch patch = patch(
                FieldUpdate.present("Augusta Ada"),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent(),
                FieldUpdate.absent());

        NaturalPerson updated = original.patchDetails(patch, EVALUATED_ON, UPDATED_AT, UPDATED_BY);

        assertEquals("Augusta Ada Lovelace", updated.displayName());
    }

    @Test
    void updateReturnsNewAggregateAndLeavesOriginalAuditUntouched() {
        NaturalPerson original = createPerson(
                null,
                details("Ada", "Lovelace", null, null, null, null));

        NaturalPerson updated = original.patchDetails(
                patch(
                        FieldUpdate.absent(),
                        FieldUpdate.absent(),
                        FieldUpdate.present("Ada"),
                        FieldUpdate.absent(),
                        FieldUpdate.absent(),
                        FieldUpdate.absent()),
                EVALUATED_ON,
                UPDATED_AT,
                UPDATED_BY);

        assertEquals(CREATED_AT, original.auditInfo().updatedAt());
        assertEquals(UPDATED_AT, updated.auditInfo().updatedAt());
        assertEquals(UPDATED_BY, updated.auditInfo().updatedBy());
    }

    @Test
    void restoresNaturalPersonWithValidState() {
        PartyId partyId = new PartyId(UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
        TenantId tenantId = new TenantId(UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));
        AuditInfo auditInfo = AuditInfo.initial(CREATED_AT, CREATED_BY);
        NaturalPersonDetails personDetails = details(
                "Ada",
                "Lovelace",
                "Ada",
                LocalDate.of(1815, Month.DECEMBER, 10),
                LocalDate.of(1852, Month.NOVEMBER, 27),
                "GB");

        NaturalPerson restored = NaturalPerson.restore(
                partyId,
                tenantId,
                "Ada Lovelace",
                PartyRecordStatus.ACTIVE,
                new PartyVersion(2),
                auditInfo,
                personDetails);

        assertEquals(partyId, restored.partyId());
        assertEquals(tenantId, restored.tenantId());
        assertEquals("Ada Lovelace", restored.displayName());
        assertEquals(PartyRecordStatus.ACTIVE, restored.recordStatus());
        assertEquals(new PartyVersion(2), restored.version());
        assertEquals(auditInfo, restored.auditInfo());
        assertEquals(personDetails, restored.details());
    }

    @Test
    void restoreRejectsNullDetails() {
        PartyId partyId = new PartyId(UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
        TenantId tenantId = new TenantId(UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));
        AuditInfo auditInfo = AuditInfo.initial(CREATED_AT, CREATED_BY);

        assertViolation(
                DomainViolation.NATURAL_PERSON_DETAILS_REQUIRED,
                () -> NaturalPerson.restore(
                        partyId,
                        tenantId,
                        "Ada Lovelace",
                        PartyRecordStatus.ACTIVE,
                        new PartyVersion(1),
                        auditInfo,
                        null));
    }

    private static NaturalPerson createPerson(String displayName, NaturalPersonDetails details) {
        return NaturalPerson.create(
                new PartyId(UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1")),
                new TenantId(UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5")),
                displayName,
                details,
                EVALUATED_ON,
                CREATED_AT,
                CREATED_BY);
    }

    private static NaturalPersonDetails details(
            String givenNames,
            String familyNames,
            String preferredName,
            LocalDate birthDate,
            LocalDate dateOfDeath,
            String birthCountryCode) {
        return new NaturalPersonDetails(
                givenNames,
                familyNames,
                preferredName,
                birthDate,
                dateOfDeath,
                birthCountryCode);
    }

    private static NaturalPersonPatch patch(
            FieldUpdate<String> givenNames,
            FieldUpdate<String> familyNames,
            FieldUpdate<String> preferredName,
            FieldUpdate<LocalDate> birthDate,
            FieldUpdate<LocalDate> dateOfDeath,
            FieldUpdate<String> birthCountryCode) {
        return new NaturalPersonPatch(
                givenNames,
                familyNames,
                preferredName,
                birthDate,
                dateOfDeath,
                birthCountryCode);
    }

    private static void assertViolation(DomainViolation violation, Runnable action) {
        DomainValidationException failure = assertThrows(DomainValidationException.class, action::run);
        assertEquals(violation, failure.violation());
    }
}
