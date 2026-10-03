package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies partial configuration merging without giving changes ownership of identity, lifecycle, or audit. */
class IdentifierSchemeChangesTest {

    @Test
    void changesOnlySuppliedDescriptionAndNameAndRetainsIdentityAndOtherConfiguration() {
        var original = original();
        var changes = new IdentifierSchemeChanges(FieldUpdate.present("New exact Name"), FieldUpdate.present(null),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent());
        var merged = changes.merge(original);
        assertEquals("New exact Name", merged.name());
        assertNull(merged.description());
        assertEquals(original.id(), merged.id());
        assertEquals(original.code(), merged.code());
        assertEquals(original.issuingCountryCode(), merged.issuingCountryCode());
        assertEquals(original.category(), merged.category());
        assertEquals(original.applicableSubjectType(), merged.applicableSubjectType());
        assertEquals(original.normalizerKey(), merged.normalizerKey());
        assertEquals(original.validatorKey(), merged.validatorKey());
        assertEquals(original.minimumLength(), merged.minimumLength());
        assertEquals(original.maximumLength(), merged.maximumLength());
        assertEquals(original.status(), merged.status());
        assertEquals(original.version(), merged.version());
        assertEquals(original.auditInfo(), merged.auditInfo());
        assertFalse(changes.hasProcessingChanges());
    }

    @Test
    void clearsOnlyNullableBoundsAndAcceptsSuppliedBooleanValues() {
        var changes = new IdentifierSchemeChanges(FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.present(null), FieldUpdate.present(BigInteger.valueOf(30)),
                FieldUpdate.present(true));
        var merged = changes.merge(original());
        assertNull(merged.minimumLength());
        assertEquals(Integer.valueOf(30), merged.maximumLength());
        assertTrue(merged.requiresExpiration());
        assertTrue(changes.hasProcessingChanges());
    }

    @Test
    void presenceRemainsSignificantEvenWhenTheSubmittedRuleIsIdentical() {
        var changes = new IdentifierSchemeChanges(FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.present("TRIM_UPPERCASE_V1"), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent());
        assertTrue(changes.hasProcessingChanges());
        assertEquals(original(), changes.merge(original()));
    }

    @Test
    void rejectsEmptyChanges() {
        FieldUpdate<String> absentText = FieldUpdate.absent();
        FieldUpdate<BigInteger> absentNumber = FieldUpdate.absent();
        FieldUpdate<Boolean> absentFlag = FieldUpdate.absent();
        assertEquals(DomainViolation.EMPTY_PATCH, assertThrows(DomainValidationException.class,
                () -> new IdentifierSchemeChanges(absentText, absentText, absentText,
                        absentText, absentNumber, absentNumber, absentFlag)).violation());
    }

    @Test
    void protectsNonnullableFieldsAtTheDomainBoundary() {
        var original = original();
        var nullName = new IdentifierSchemeChanges(FieldUpdate.present(null), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent());
        assertEquals(DomainViolation.IDENTIFIER_SCHEME_NAME_REQUIRED,
                assertThrows(DomainValidationException.class, () -> nullName.merge(original)).violation());
        var nullFlag = new IdentifierSchemeChanges(FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.present(null));
        assertEquals(DomainViolation.IDENTIFIER_SCHEME_EXPIRATION_METADATA_REQUIRED,
                assertThrows(DomainValidationException.class, () -> nullFlag.merge(original)).violation());
    }

    @Test
    void validatesChangedBoundsAgainstRetainedBoundsBeforeCreatingACandidate() {
        var changes = new IdentifierSchemeChanges(FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.present(BigInteger.valueOf(5)), FieldUpdate.absent());
        var original = original();
        assertEquals(DomainViolation.IDENTIFIER_LENGTH_RANGE_INVALID,
                assertThrows(DomainValidationException.class, () -> changes.merge(original)).violation());
        assertEquals(original(), original);
    }

    @Test
    void doesNotExposeImmutablePropertiesAsChangeInputs() {
        var forbidden = Set.of("id", "code", "issuingCountryCode", "category", "applicableSubjectType",
                "status", "version", "auditInfo");
        assertFalse(Arrays.stream(IdentifierSchemeChanges.class.getRecordComponents())
                .anyMatch(component -> forbidden.contains(component.getName())));
    }

    private static IdentifierScheme original() {
        return new IdentifierScheme(new IdentifierSchemeId(UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd998")),
                "Exact_Code", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, "Original name",
                "Original description", "TRIM_UPPERCASE_V1", "ALPHANUMERIC_V1", 10, 20, false,
                IdentifierSchemeStatus.DRAFT, new IdentifierSchemeVersion(4),
                AuditInfo.initial(Instant.parse("2026-09-30T00:00:00Z"), "catalog-actor"));
    }
}
