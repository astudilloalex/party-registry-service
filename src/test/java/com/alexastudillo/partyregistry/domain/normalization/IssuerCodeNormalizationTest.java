package com.alexastudillo.partyregistry.domain.normalization;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies optional issuing-entity code syntax, bounded blank mapping, and locale-independent new-write text. */
class IssuerCodeNormalizationTest {

    @Test
    void canonicalizesAsciiLetterGroupsWithoutConsultingAnyCountryCatalog() {
        assertEquals("CIVIL_REGISTRY", IssuerCodeNormalization.forWrite("civil_registry"));
        assertEquals("AUTHORITY", IssuerCodeNormalization.forWrite("Authority"));
        assertEquals("A", IssuerCodeNormalization.forWrite("a"));
        String maximum = "a".repeat(64);
        assertEquals("A".repeat(64), IssuerCodeNormalization.forWrite(maximum));
    }

    @Test
    void nullEmptyAndBoundedJavaBlankInputBecomeNull() {
        assertNull(IssuerCodeNormalization.forWrite(null));
        for (String blank : List.of("", " ", "\t\n", "\u2003", " ".repeat(64))) {
            assertNull(IssuerCodeNormalization.forWrite(blank));
        }
    }

    @Test
    void rejectsInvalidSeparatorsNonAsciiLettersDigitsControlsAndNonblankWhitespace() {
        for (String invalid : List.of("_REGISTRY", "REGISTRY_", "CIVIL__REGISTRY", "_", "REGISTRY1",
                "REGÍSTRY", "ß", "CIVIL-REGISTRY", "CIVIL REGISTRY", " registry ", "REG\0ISTRY")) {
            var failure = assertThrows(DomainValidationException.class, () -> IssuerCodeNormalization.forWrite(invalid));
            assertEquals(DomainViolation.IDENTIFIER_ISSUER_CODE_INVALID, failure.violation(), invalid);
        }
    }

    @Test
    void inputLengthIsCheckedBeforeBlankMappingOrCanonicalization() {
        for (String oversized : List.of("a".repeat(65), " ".repeat(65))) {
            var failure = assertThrows(DomainValidationException.class, () -> IssuerCodeNormalization.forWrite(oversized));
            assertEquals(DomainViolation.IDENTIFIER_ISSUER_CODE_TOO_LONG, failure.violation());
        }
    }
}
