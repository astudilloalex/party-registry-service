package com.alexastudillo.partyregistry.domain;

import static com.alexastudillo.partyregistry.domain.DomainContractSupport.assertDomainViolation;
import static com.alexastudillo.partyregistry.domain.DomainContractSupport.construct;
import static com.alexastudillo.partyregistry.domain.DomainContractSupport.enumValue;
import static com.alexastudillo.partyregistry.domain.DomainContractSupport.invoke;
import static com.alexastudillo.partyregistry.domain.DomainContractSupport.invokeStatic;
import static com.alexastudillo.partyregistry.domain.DomainContractSupport.type;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class IdentifierDomainContractTest {
    private static final UUID IDENTIFIER_ID = UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890b1");
    private static final UUID TENANT_ID = UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890b2");
    private static final UUID PARTY_ID = UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890b3");
    private static final UUID SCHEME_ID = UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890b4");
    private static final String HASH = "A".repeat(64);

    @ParameterizedTest
    @CsvSource({"NATURAL_PERSON,NATURAL_PERSON", "LEGAL_ENTITY,LEGAL_ENTITY", "NATURAL_PERSON,BOTH", "LEGAL_ENTITY,BOTH"})
    void compatiblePartyAndSchemeSubjectTypesAreAccepted(String partyType, String subjectType) {
        invokeStatic(
                "IdentifierSchemePolicy", "validateIdentifier", enumValue("PartyType", partyType),
                enumValue("IdentifierSubjectType", subjectType), 4, 12, "12345678", true, true);
    }

    @ParameterizedTest
    @CsvSource({"NATURAL_PERSON,LEGAL_ENTITY", "LEGAL_ENTITY,NATURAL_PERSON"})
    void incompatiblePartyAndSchemeSubjectTypesAreRejected(String partyType, String subjectType) {
        assertDomainViolation(() -> invokeStatic(
                "IdentifierSchemePolicy", "validateIdentifier", enumValue("PartyType", partyType),
                enumValue("IdentifierSubjectType", subjectType), 4, 12, "12345678", true, true));
    }

    @ParameterizedTest
    @MethodSource("invalidLengthConfigurations")
    void configuredLengthBoundsMustBePositiveAndOrdered(Integer minimum, Integer maximum) {
        assertDomainViolation(() -> invokeStatic("IdentifierSchemePolicy", "validateConfiguration", minimum, maximum));
    }

    @ParameterizedTest
    @CsvSource({"5,12,1234", "4,7,12345678"})
    void normalizedIdentifierMustFitConfiguredLength(Integer minimum, Integer maximum, String normalized) {
        assertDomainViolation(() -> invokeStatic(
                "IdentifierSchemePolicy", "validateIdentifier", enumValue("PartyType", "NATURAL_PERSON"),
                enumValue("IdentifierSubjectType", "BOTH"), minimum, maximum, normalized, true, true));
    }

    @ParameterizedTest
    @CsvSource({"false,true", "true,false", "false,false"})
    void unavailableNormalizerOrValidatorFailsClosed(boolean normalizerAvailable, boolean validatorAvailable) {
        assertDomainViolation(() -> invokeStatic(
                "IdentifierSchemePolicy", "validateIdentifier", enumValue("PartyType", "NATURAL_PERSON"),
                enumValue("IdentifierSubjectType", "BOTH"), 4, 12, "12345678", normalizerAvailable, validatorAvailable));
    }

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @MethodSource("identifierTransitions")
    void identifierLifecycleImplementsTheCompleteTransitionMatrix(String source, String target, boolean allowed) {
        Object from = enumValue("PartyIdentifierStatus", source);
        Object to = enumValue("PartyIdentifierStatus", target);
        Object[] evidence = transitionEvidence(target);
        if (allowed) {
            assertEquals(to, invokeStatic("PartyIdentifierLifecycle", "transition", from, to, evidence[0], evidence[1], evidence[2], evidence[3]));
        } else {
            assertDomainViolation(() -> invokeStatic(
                    "PartyIdentifierLifecycle", "transition", from, to, evidence[0], evidence[1], evidence[2], evidence[3]));
        }
    }

    @Test
    void identifierInitialStateIsPendingVerification() {
        assertEquals(
                enumValue("PartyIdentifierStatus", "PENDING_VERIFICATION"),
                invokeStatic("PartyIdentifierLifecycle", "initialStatus"));
    }

    @ParameterizedTest
    @CsvSource({"1", "2", "32767"})
    void positiveVersionValuesAcceptTheirLowerBoundaryAndRepresentativeValues(int value) {
        Object version = invokeStatic("PositiveVersion", "of", value);
        assertEquals(value, invoke(version, "value"));
    }

    @ParameterizedTest
    @CsvSource({"0", "-1"})
    void positiveVersionValuesRejectZeroAndNegativeValues(int value) {
        assertDomainViolation(() -> invokeStatic("PositiveVersion", "of", value));
    }

    @ParameterizedTest
    @CsvSource({"0", "1", "9223372036854775807"})
    void nonNegativeVersionValuesAcceptZeroAndPositiveValues(long value) {
        Object version = invokeStatic("NonNegativeVersion", "of", value);
        assertEquals(value, invoke(version, "value"));
    }

    @Test
    void nonNegativeVersionValuesRejectNegativeValues() {
        assertDomainViolation(() -> invokeStatic("NonNegativeVersion", "of", -1L));
    }

    @Test
    void verifiedAndExpiredTransitionsRequireTheirStateEvidenceAndChronologicalDates() {
        Object pending = enumValue("PartyIdentifierStatus", "PENDING_VERIFICATION");
        Object verified = enumValue("PartyIdentifierStatus", "VERIFIED");
        assertDomainViolation(() -> invokeStatic(
                "PartyIdentifierLifecycle", "transition", pending, verified, null, null, null, null));

        Object currentVerified = enumValue("PartyIdentifierStatus", "VERIFIED");
        Object expired = enumValue("PartyIdentifierStatus", "EXPIRED");
        assertDomainViolation(() -> invokeStatic(
                "PartyIdentifierLifecycle", "transition", currentVerified, expired,
                LocalDate.of(2026, 8, 15), null, Instant.parse("2026-08-15T12:00:00Z"), "synthetic-user"));

        assertDomainViolation(() -> invokeStatic(
                "PartyIdentifierLifecycle", "transition", currentVerified, expired,
                LocalDate.of(2026, 8, 16), LocalDate.of(2026, 8, 15),
                Instant.parse("2026-08-15T12:00:00Z"), "synthetic-user"));
    }

    @Test
    void permanentUniquenessKeyDependsOnlyOnTenantSchemeAndFingerprint() {
        Object original = construct("IdentifierUniquenessKey", TENANT_ID, SCHEME_ID, HASH);
        Object same = construct("IdentifierUniquenessKey", TENANT_ID, SCHEME_ID, HASH);
        Object otherTenant = construct(
                "IdentifierUniquenessKey", UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890b5"), SCHEME_ID, HASH);
        Object otherScheme = construct(
                "IdentifierUniquenessKey", TENANT_ID, UUID.fromString("018f0c72-4a7b-7c91-8b2a-1234567890b6"), HASH);

        assertEquals(original, same);
        assertNotEquals(original, otherTenant);
        assertNotEquals(original, otherScheme);
    }

    @Test
    void identifierDescriptiveUpdatePreservesAllImmutableOwnershipAndValueFacts() {
        Object original = construct(
                "PartyIdentifierIdentity", IDENTIFIER_ID, TENANT_ID, PARTY_ID, SCHEME_ID, "12345678", "ISSUER-A");
        Object updated = invoke(original, "withIssuerCode", "ISSUER-B");

        assertNotSame(original, updated);
        assertEquals("ISSUER-A", invoke(original, "issuerCode"));
        assertEquals("ISSUER-B", invoke(updated, "issuerCode"));
        assertEquals(IDENTIFIER_ID, invoke(updated, "id"));
        assertEquals(TENANT_ID, invoke(updated, "tenantId"));
        assertEquals(PARTY_ID, invoke(updated, "partyId"));
        assertEquals(SCHEME_ID, invoke(updated, "schemeId"));
        assertEquals("12345678", invoke(updated, "normalizedValue"));
    }

    @Test
    void protectedIdentifierContainsOnlyProtectedRepresentationsAndPositiveVersions() {
        Object protectedValue = invokeStatic(
                "ProtectedIdentifier", "create", "synthetic-ciphertext", 1, HASH, "****5678", 1);
        assertEquals(HASH, invoke(protectedValue, "normalizedValueHash"));
        assertEquals("****5678", invoke(protectedValue, "maskedValue"));
        assertFalse(Arrays.stream(type("ProtectedIdentifier").getDeclaredFields())
                .map(field -> field.getName().toLowerCase())
                .anyMatch(name -> name.equals("plaintext") || name.equals("normalizedvalue")));

        assertDomainViolation(() -> invokeStatic(
                "ProtectedIdentifier", "create", "synthetic-ciphertext", 0, HASH, "****5678", 1));
        assertDomainViolation(() -> invokeStatic(
                "ProtectedIdentifier", "create", "synthetic-ciphertext", 1, "a".repeat(64), "****5678", 1));
        assertDomainViolation(() -> invokeStatic(
                "ProtectedIdentifier", "create", "synthetic-ciphertext", 1, HASH, "****5678", 0));
    }

    private static Stream<Arguments> invalidLengthConfigurations() {
        return Stream.of(
                Arguments.of(0, null), Arguments.of(-1, 5), Arguments.of(null, 0), Arguments.of(8, 7));
    }

    private static Stream<Arguments> identifierTransitions() {
        Set<String> allowed = Set.of(
                "PENDING_VERIFICATION->VERIFIED", "PENDING_VERIFICATION->REJECTED",
                "PENDING_VERIFICATION->REVOKED", "VERIFIED->EXPIRED", "VERIFIED->REVOKED",
                "REJECTED->REVOKED", "EXPIRED->REVOKED");
        return Stream.of("PENDING_VERIFICATION", "VERIFIED", "REJECTED", "EXPIRED", "REVOKED")
                .flatMap(source -> Stream.of("PENDING_VERIFICATION", "VERIFIED", "REJECTED", "EXPIRED", "REVOKED")
                        .map(target -> Arguments.of(source, target, allowed.contains(source + "->" + target))));
    }

    private static Object[] transitionEvidence(String target) {
        LocalDate issued = LocalDate.of(2026, 8, 14);
        LocalDate expiry = target.equals("EXPIRED") ? LocalDate.of(2026, 8, 15) : null;
        Instant verifiedAt = target.equals("VERIFIED") ? Instant.parse("2026-08-15T12:00:00Z") : null;
        String verifiedBy = target.equals("VERIFIED") ? "synthetic-user" : null;
        return new Object[] {issued, expiry, verifiedAt, verifiedBy};
    }
}
