package com.alexastudillo.partyregistry.domain;

import static com.alexastudillo.partyregistry.domain.DomainContractSupport.assertDomainViolation;
import static com.alexastudillo.partyregistry.domain.DomainContractSupport.construct;
import static com.alexastudillo.partyregistry.domain.DomainContractSupport.enumValue;
import static com.alexastudillo.partyregistry.domain.DomainContractSupport.invoke;
import static com.alexastudillo.partyregistry.domain.DomainContractSupport.invokeStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class PartyDomainContractTest {
    private static final UUID PARTY_ID = UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890ab");
    private static final UUID TENANT_ID = UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890ac");

    @ParameterizedTest(name = "{0} accepts only {1} details")
    @CsvSource({"NATURAL_PERSON,NATURAL_PERSON", "LEGAL_ENTITY,LEGAL_ENTITY"})
    void matchingPartyTypeAndDetailKindIsAccepted(String partyType, String detailKind) {
        invokeStatic("PartyDetailPolicy", "validate", enumValue("PartyType", partyType), enumValue("DetailKind", detailKind));
    }

    @ParameterizedTest(name = "{0} rejects {1} details")
    @CsvSource({"NATURAL_PERSON,LEGAL_ENTITY", "LEGAL_ENTITY,NATURAL_PERSON"})
    void conflictingPartyTypeAndDetailKindIsRejected(String partyType, String detailKind) {
        assertDomainViolation(() -> invokeStatic(
                "PartyDetailPolicy", "validate", enumValue("PartyType", partyType), enumValue("DetailKind", detailKind)));
    }

    @ParameterizedTest
    @MethodSource("validLifeDates")
    void equalChronologicalOrPartiallyUnknownLifeDatesAreAccepted(LocalDate start, LocalDate end) {
        invokeStatic("LifeDatePolicy", "validate", start, end);
    }

    @Test
    void lifecycleEndBeforeStartIsRejected() {
        assertDomainViolation(() -> invokeStatic(
                "LifeDatePolicy", "validate", LocalDate.of(2000, 1, 2), LocalDate.of(2000, 1, 1)));
    }

    @ParameterizedTest
    @CsvSource({"EC", "US", "ZZ"})
    void exactlyTwoUppercaseCountryCharactersAreAccepted(String code) {
        Object country = invokeStatic("CountryCode", "of", code);
        assertEquals(code, invoke(country, "value"));
    }

    @ParameterizedTest
    @CsvSource(value = {"E", "ECU", "ec", "E1", "' E'", "'EC '"})
    void malformedCountryCodesAreRejected(String code) {
        assertDomainViolation(() -> invokeStatic("CountryCode", "of", code));
    }

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @MethodSource("partyTransitions")
    void partyLifecycleImplementsTheCompleteTransitionMatrix(String source, String target, boolean allowed) {
        Object from = enumValue("PartyStatus", source);
        Object to = enumValue("PartyStatus", target);
        if (allowed) {
            assertEquals(to, invokeStatic("PartyLifecycle", "transition", from, to));
        } else {
            assertDomainViolation(() -> invokeStatic("PartyLifecycle", "transition", from, to));
        }
    }

    @Test
    void partyInitialStateIsDraft() {
        assertEquals(enumValue("PartyStatus", "DRAFT"), invokeStatic("PartyLifecycle", "initialStatus"));
    }

    @Test
    void descriptiveUpdateCreatesANewValueAndPreservesImmutablePartyIdentity() {
        Object original = construct(
                "PartyIdentity", PARTY_ID, TENANT_ID, enumValue("PartyType", "NATURAL_PERSON"), "Ada Lovelace");
        Object updated = invoke(original, "withDisplayName", "Ada Byron");

        assertNotSame(original, updated);
        assertEquals("Ada Lovelace", invoke(original, "displayName"));
        assertEquals("Ada Byron", invoke(updated, "displayName"));
        assertEquals(PARTY_ID, invoke(updated, "id"));
        assertEquals(TENANT_ID, invoke(updated, "tenantId"));
        assertEquals(enumValue("PartyType", "NATURAL_PERSON"), invoke(updated, "type"));
    }

    private static Stream<Arguments> validLifeDates() {
        LocalDate date = LocalDate.of(2000, 1, 1);
        return Stream.of(
                Arguments.of(date, date),
                Arguments.of(date, date.plusDays(1)),
                Arguments.of(null, date),
                Arguments.of(date, null));
    }

    private static Stream<Arguments> partyTransitions() {
        Set<String> allowed = Set.of(
                "DRAFT->ACTIVE", "DRAFT->ARCHIVED", "ACTIVE->INACTIVE", "ACTIVE->ARCHIVED",
                "INACTIVE->ACTIVE", "INACTIVE->ARCHIVED");
        return Stream.of("DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED")
                .flatMap(source -> Stream.of("DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED")
                        .map(target -> Arguments.of(source, target, allowed.contains(source + "->" + target))));
    }
}
