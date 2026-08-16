package com.alexastudillo.partyregistry.domain;

import static com.alexastudillo.partyregistry.domain.DomainContractSupport.invokeStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class DisplayNameAndMaskContractTest {
    @ParameterizedTest
    @MethodSource("naturalPersonNames")
    void naturalPersonDisplayNameNormalizesEachPartAndJoinsWithOneAsciiSpace(
            String givenNames, String familyNames, String expected) {
        assertEquals(expected, invokeStatic("DisplayName", "forNaturalPerson", givenNames, familyNames));
    }

    @ParameterizedTest
    @MethodSource("legalNames")
    void legalEntityDisplayNameNormalizesOnlyTheLegalName(String legalName, String expected) {
        assertEquals(expected, invokeStatic("DisplayName", "forLegalEntity", legalName));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "'',****", "1,****", "12,****", "123,****", "1234,****",
        "12345,****2345", "0123456789,****6789", "ÁBCDE,****BCDE"
    })
    void maskHasExactBoundariesAndUsesTheNormalizedSuffix(String normalized, String expected) {
        assertEquals(expected, invokeStatic("IdentifierMask", "mask", normalized));
    }

    private static Stream<Arguments> naturalPersonNames() {
        return Stream.of(
                Arguments.of("  Ada  ", "  Lovelace ", "Ada Lovelace"),
                Arguments.of("María\tJosé", "de\n la  Cruz", "María José de la Cruz"),
                Arguments.of("Élodie", "D'Alembert", "Élodie D'Alembert"));
    }

    private static Stream<Arguments> legalNames() {
        return Stream.of(
                Arguments.of("  Acme\t Holdings  S.A. ", "Acme Holdings S.A."),
                Arguments.of("Société\nGénérale", "Société Générale"));
    }
}
