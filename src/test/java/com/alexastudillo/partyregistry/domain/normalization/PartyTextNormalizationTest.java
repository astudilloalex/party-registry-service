package com.alexastudillo.partyregistry.domain.normalization;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies deterministic write normalization without changing nulls, interior spaces, or validation boundaries. */
class PartyTextNormalizationTest {

    @Test
    void preservesNullsAccentsPunctuationAndInteriorWhitespaceIndependentlyOfLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertNull(PartyTextNormalization.uppercase(null));
            assertEquals("", PartyTextNormalization.uppercase(" \t\n "));
            assertEquals("IRIS  ORDO\u00d1EZ O'NEILL", PartyTextNormalization.uppercase("  iris  ordo\u00f1ez o'neill  "));
            assertEquals("IT", PartyTextNormalization.countryCode(" it "));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void normalizesBeforeWriteValidationAndPreservesInvalidCountryInputs() {
        NaturalPersonDetails natural = NaturalPersonDetails.forWrite(
                " ".repeat(300) + "Ada", " Lovelace ", "  ", null, null, " gB ");
        assertEquals("ADA", natural.givenNames());
        assertEquals("LOVELACE", natural.familyNames());
        assertEquals("", natural.preferredName());
        assertEquals("GB", natural.birthCountryCode());
        assertEquals("ADA LOVELACE", natural.derivedDisplayName());
        LegalEntityDetails legal = LegalEntityDetails.forWrite(
                " Example Ltd ", " Example ", " ltd ", " ec ", null, null);
        assertEquals("EXAMPLE LTD", legal.legalName());
        assertEquals("EXAMPLE", legal.tradeName());
        assertEquals("LTD", legal.legalFormCode());
        assertEquals("EC", legal.incorporationCountryCode());
        assertNull(PartyTextNormalization.countryCode(null));
        assertEquals("\u00df", PartyTextNormalization.countryCode(" \u00df "));
        assertEquals(DomainViolation.BIRTH_COUNTRY_CODE_INVALID, assertThrows(DomainValidationException.class,
                () -> NaturalPersonDetails.forWrite("Ada", "Lovelace", null, null, null, "\u00df")).violation());
    }

    @Test
    void rejectsUppercaseExpansionOverflowWithoutTruncating() {
        assertEquals(DomainViolation.GIVEN_NAMES_TOO_LONG, assertThrows(DomainValidationException.class,
                () -> NaturalPersonDetails.forWrite("\u00df".repeat(101), "Family", null, null, null, null)).violation());
        assertEquals(DomainViolation.LEGAL_FORM_CODE_TOO_LONG, assertThrows(DomainValidationException.class,
                () -> LegalEntityDetails.forWrite("Example", null, "\u00df".repeat(33), "EC", null, null)).violation());
    }
}
