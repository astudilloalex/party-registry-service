package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies framework-independent scheme creation, exact catalog text, and admission-field invariants. */
class IdentifierSchemeCreationTest {

    private static final IdentifierSchemeId ID = new IdentifierSchemeId(
            UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd999"));
    private static final Instant CREATED = Instant.parse("2026-09-30T00:00:00Z");
    private static final String NORMALIZER = "TRIM_UPPERCASE_V1";
    private static final String VALIDATOR = "ALPHANUMERIC_V1";

    @Test
    void createsAnExactDraftAtVersionZeroWithEqualInitialAuditValues() {
        var scheme = create(" Mixed_Code ", "EC", IdentifierCategory.OTHER, IdentifierSubjectType.BOTH,
                " Mixed case name ", null, NORMALIZER, VALIDATOR, false);
        assertEquals(ID, scheme.id());
        assertEquals(" Mixed_Code ", scheme.code());
        assertEquals(" Mixed case name ", scheme.name());
        assertEquals(IdentifierSchemeStatus.DRAFT, scheme.status());
        assertEquals(0, scheme.version().value());
        assertEquals(new AuditInfo(CREATED, "catalog-actor", CREATED, "catalog-actor"), scheme.auditInfo());
        assertNull(scheme.description());
        assertNull(scheme.minimumLength());
        assertNull(scheme.maximumLength());
        assertFalse(scheme.requiresExpiration());
    }

    @Test
    void legacyExpirationMetadataRemainsAnOrdinaryBoolean() {
        var scheme = create("LEGACY", "EC", IdentifierCategory.PASSPORT, IdentifierSubjectType.BOTH,
                "Legacy scheme", "Historical metadata", NORMALIZER, VALIDATOR, true);
        assertTrue(scheme.requiresExpiration());
        assertEquals("Historical metadata", scheme.description());
        assertTrue(scheme.supports(PartyType.NATURAL_PERSON));
        assertTrue(scheme.supports(PartyType.LEGAL_ENTITY));
    }

    @ParameterizedTest
    @MethodSource("enumCombinations")
    void acceptsEveryDeclaredCategoryAndSubjectType(IdentifierCategory category, IdentifierSubjectType subject) {
        var scheme = create("ENUMS", "EC", category, subject, "Declared enum", null,
                NORMALIZER, VALIDATOR, false);
        assertEquals(category, scheme.category());
        assertEquals(subject, scheme.applicableSubjectType());
    }

    @ParameterizedTest
    @MethodSource("textLimits")
    void countsUnicodeCodePointsAtEachTextBoundary(String property, int limit, DomainViolation violation) {
        String boundary = "\uD801\uDC00".repeat(limit);
        assertEquals(boundary, propertyValue(withText(property, boundary), property));
        assertEquals(violation, assertThrows(DomainValidationException.class,
                () -> withText(property, boundary + "A")).violation());
    }

    @ParameterizedTest
    @MethodSource("requiredText")
    void rejectsMissingAndBlankRequiredText(String property, DomainViolation violation) {
        for (String value : new String[] {null, "", " \t "}) {
            assertEquals(violation, assertThrows(DomainValidationException.class,
                    () -> withText(property, value)).violation());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ec", " EC", "EC ", "E", "ECU", "12", "\u00C9C"})
    void rejectsNoncanonicalIssuingCountry(String country) {
        assertEquals(DomainViolation.IDENTIFIER_ISSUING_COUNTRY_CODE_INVALID,
                assertThrows(DomainValidationException.class,
                        () -> withText("country", country)).violation());
    }

    @Test
    void rejectsMissingCategoryAndSubjectType() {
        assertEquals(DomainViolation.IDENTIFIER_CATEGORY_REQUIRED, assertThrows(DomainValidationException.class,
                () -> create("CODE", "EC", null, IdentifierSubjectType.BOTH, "Name", null,
                        NORMALIZER, VALIDATOR, false)).violation());
        assertEquals(DomainViolation.IDENTIFIER_SUBJECT_TYPE_REQUIRED, assertThrows(DomainValidationException.class,
                () -> create("CODE", "EC", IdentifierCategory.OTHER, null, "Name", null,
                        NORMALIZER, VALIDATOR, false)).violation());
    }

    private static IdentifierScheme withText(String property, String value) {
        return create(property.equals("code") ? value : "CODE", property.equals("country") ? value : "EC",
                IdentifierCategory.OTHER, IdentifierSubjectType.BOTH, property.equals("name") ? value : "Name",
                property.equals("description") ? value : null, property.equals("normalizer") ? value : NORMALIZER,
                property.equals("validator") ? value : VALIDATOR, false);
    }

    private static String propertyValue(IdentifierScheme scheme, String property) {
        return switch (property) {
            case "code" -> scheme.code();
            case "name" -> scheme.name();
            case "description" -> scheme.description();
            case "normalizer" -> scheme.normalizerKey();
            case "validator" -> scheme.validatorKey();
            default -> throw new IllegalArgumentException("Unknown fixture property");
        };
    }

    private static IdentifierScheme create(String code, String country, IdentifierCategory category,
            IdentifierSubjectType subject, String name, String description, String normalizer,
            String validator, boolean requiresExpiration) {
        return IdentifierScheme.create(ID, code, country, category, subject, name, description,
                normalizer, validator, null, null, requiresExpiration, CREATED, "catalog-actor");
    }

    private static Stream<Arguments> textLimits() {
        return Stream.of(Arguments.of("code", 64, DomainViolation.IDENTIFIER_SCHEME_CODE_TOO_LONG),
                Arguments.of("name", 150, DomainViolation.IDENTIFIER_SCHEME_NAME_TOO_LONG),
                Arguments.of("description", 500, DomainViolation.IDENTIFIER_SCHEME_DESCRIPTION_TOO_LONG),
                Arguments.of("normalizer", 64, DomainViolation.IDENTIFIER_NORMALIZER_KEY_TOO_LONG),
                Arguments.of("validator", 64, DomainViolation.IDENTIFIER_VALIDATOR_KEY_TOO_LONG));
    }

    private static Stream<Arguments> requiredText() {
        return Stream.of(Arguments.of("code", DomainViolation.IDENTIFIER_SCHEME_CODE_REQUIRED),
                Arguments.of("country", DomainViolation.IDENTIFIER_ISSUING_COUNTRY_CODE_REQUIRED),
                Arguments.of("name", DomainViolation.IDENTIFIER_SCHEME_NAME_REQUIRED),
                Arguments.of("normalizer", DomainViolation.IDENTIFIER_NORMALIZER_KEY_REQUIRED),
                Arguments.of("validator", DomainViolation.IDENTIFIER_VALIDATOR_KEY_REQUIRED));
    }

    private static Stream<Arguments> enumCombinations() {
        return Stream.of(IdentifierCategory.values()).flatMap(category -> Stream.of(IdentifierSubjectType.values())
                .map(subject -> Arguments.of(category, subject)));
    }
}
