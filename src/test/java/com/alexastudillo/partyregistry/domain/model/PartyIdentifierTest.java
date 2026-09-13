package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies PartyIdentifier independence, protected state, and initial lifecycle
 * invariants.
 */
class PartyIdentifierTest {

    private static final PartyIdentifierId IDENTIFIER_ID = new PartyIdentifierId(
            UUID.fromString("0198d15c-0557-7a16-a6a8-e3f0ddb5b744"));
    private static final TenantId TENANT_ID = new TenantId(
            UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));
    private static final PartyId PARTY_ID = new PartyId(
            UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
    private static final IdentifierSchemeId SCHEME_ID = new IdentifierSchemeId(
            UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd605"));
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T10:00:00Z");
    private static final LocalDate ISSUED_ON = LocalDate.of(2026, Month.JANUARY, 1);
    private static final LocalDate EXPIRES_ON = LocalDate.of(2030, Month.JANUARY, 1);

    @Test
    void createsPendingNonPrimaryIdentifierAtVersionZeroByDefault() {
        PartyIdentifier identifier = PartyIdentifier.builder()
                .identifierId(IDENTIFIER_ID)
                .tenantId(TENANT_ID)
                .partyId(PARTY_ID)
                .identifierSchemeId(SCHEME_ID)
                .protectedValue(protectedValue())
                .issuerCode("AUTHORITY")
                .issuedOn(ISSUED_ON)
                .expiresOn(EXPIRES_ON)
                .created(CREATED_AT, "creator")
                .build();

        assertEquals(IDENTIFIER_ID, identifier.identifierId());
        assertEquals(TENANT_ID, identifier.tenantId());
        assertEquals(PARTY_ID, identifier.partyId());
        assertEquals(SCHEME_ID, identifier.identifierSchemeId());
        assertEquals(PartyIdentifierStatus.PENDING_VERIFICATION, identifier.status());
        assertEquals(PartyIdentifierVersion.initial(), identifier.version());
        assertFalse(identifier.isPrimary());
        assertNull(identifier.verifiedAt());
        assertNull(identifier.verifiedBy());
        assertEquals(AuditInfo.initial(CREATED_AT, "creator"), identifier.auditInfo());
    }

    @Test
    void preservesAnExplicitPrimaryFlag() {
        PartyIdentifier identifier = PartyIdentifier.builder()
                .identifierId(IDENTIFIER_ID)
                .tenantId(TENANT_ID)
                .partyId(PARTY_ID)
                .identifierSchemeId(SCHEME_ID)
                .protectedValue(protectedValue())
                .primary(true)
                .created(CREATED_AT, "creator")
                .build();

        assertTrue(identifier.isPrimary());
    }

    @Test
    void rejectsIncoherentValidityDatesAndOversizedIssuer() {
        assertViolation(DomainViolation.IDENTIFIER_VALIDITY_DATE_ORDER,
                () -> PartyIdentifier.builder()
                        .identifierId(IDENTIFIER_ID)
                        .tenantId(TENANT_ID)
                        .partyId(PARTY_ID)
                        .identifierSchemeId(SCHEME_ID)
                        .protectedValue(protectedValue())
                        .issuedOn(EXPIRES_ON)
                        .expiresOn(ISSUED_ON)
                        .created(CREATED_AT, "creator")
                        .build());
        assertViolation(DomainViolation.IDENTIFIER_ISSUER_CODE_TOO_LONG,
                () -> PartyIdentifier.builder()
                        .identifierId(IDENTIFIER_ID)
                        .tenantId(TENANT_ID)
                        .partyId(PARTY_ID)
                        .identifierSchemeId(SCHEME_ID)
                        .protectedValue(protectedValue())
                        .issuerCode("I".repeat(65))
                        .created(CREATED_AT, "creator")
                        .build());
    }

    @Test
    void restoresVerifiedStateOnlyWithVerificationMetadata() {
        Instant verifiedAt = Instant.parse("2026-08-30T11:00:00Z");
        PartyIdentifier restored = restore(
                PartyIdentifierStatus.VERIFIED,
                EXPIRES_ON,
                false,
                verifiedAt,
                "verifier");

        assertEquals(PartyIdentifierStatus.VERIFIED, restored.status());
        assertEquals(verifiedAt, restored.verifiedAt());
        assertEquals("verifier", restored.verifiedBy());
        assertViolation(DomainViolation.IDENTIFIER_VERIFICATION_TIMESTAMP_REQUIRED,
                () -> restore(PartyIdentifierStatus.VERIFIED, EXPIRES_ON, false, null, "verifier"));
        assertViolation(DomainViolation.IDENTIFIER_VERIFICATION_USER_REQUIRED,
                () -> restore(PartyIdentifierStatus.VERIFIED, EXPIRES_ON, false, verifiedAt, "  "));
    }

    @Test
    void requiresAnExpirationDateForExpiredState() {
        assertViolation(DomainViolation.IDENTIFIER_EXPIRATION_DATE_REQUIRED,
                () -> restore(PartyIdentifierStatus.EXPIRED, null, false, null, null));
    }

    @Test
    void expirationUsesEarlierThanEvaluationSemantics() {
        PartyIdentifier identifier = restore(
                PartyIdentifierStatus.VERIFIED,
                EXPIRES_ON,
                false,
                CREATED_AT,
                "verifier");

        assertFalse(identifier.isExpiredOn(EXPIRES_ON));
        assertTrue(identifier.isExpiredOn(EXPIRES_ON.plusDays(1)));
        assertViolation(DomainViolation.EVALUATION_DATE_REQUIRED, () -> identifier.isExpiredOn(null));
    }

    @Test
    void validatesAllProtectedRepresentationFields() {
        ProtectedIdentifierValue boundary = new ProtectedIdentifierValue(
                "v1.ciphertext",
                1,
                "a".repeat(64),
                "M".repeat(64),
                new IdentifierRuleVersion(1));
        assertEquals(64, boundary.maskedValue().length());

        assertViolation(DomainViolation.ENCRYPTED_IDENTIFIER_VALUE_REQUIRED,
                () -> new ProtectedIdentifierValue(" ", 1, "a".repeat(64), "***1234",
                        new IdentifierRuleVersion(1)));
        assertViolation(DomainViolation.ENCRYPTION_KEY_VERSION_INVALID,
                () -> new ProtectedIdentifierValue("ciphertext", 0, "a".repeat(64), "***1234",
                        new IdentifierRuleVersion(1)));
        assertViolation(DomainViolation.NORMALIZED_IDENTIFIER_HASH_REQUIRED,
                () -> new ProtectedIdentifierValue("ciphertext", 1, null, "***1234",
                        new IdentifierRuleVersion(1)));
        assertViolation(DomainViolation.NORMALIZED_IDENTIFIER_HASH_INVALID,
                () -> new ProtectedIdentifierValue("ciphertext", 1, "z".repeat(64), "***1234",
                        new IdentifierRuleVersion(1)));
        assertViolation(DomainViolation.MASKED_IDENTIFIER_VALUE_REQUIRED,
                () -> new ProtectedIdentifierValue("ciphertext", 1, "a".repeat(64), " ",
                        new IdentifierRuleVersion(1)));
        assertViolation(DomainViolation.MASKED_IDENTIFIER_VALUE_TOO_LONG,
                () -> new ProtectedIdentifierValue("ciphertext", 1, "a".repeat(64), "M".repeat(65),
                        new IdentifierRuleVersion(1)));
        assertViolation(DomainViolation.NORMALIZATION_VERSION_INVALID,
                () -> new ProtectedIdentifierValue("ciphertext", 1, "a".repeat(64), "***1234", null));
    }

    @Test
    void aggregateContainsNoPlaintextAndPartiesOwnNoIdentifiers() {
        assertFalse(Arrays.stream(PartyIdentifier.class.getDeclaredFields())
                .map(Field::getName)
                .anyMatch(name -> name.equals("value")
                        || name.equals("completeValue")
                        || name.equals("normalizedValue")));
        assertFalse(ownsIdentifiers(NaturalPerson.class));
        assertFalse(ownsIdentifiers(LegalEntity.class));
    }

    private static boolean ownsIdentifiers(Class<?> partyClass) {
        return Arrays.stream(partyClass.getDeclaredFields())
                .anyMatch(field -> field.getType() == PartyIdentifier.class
                        || field.getType().isArray()
                                && field.getType()
                                        .componentType() == PartyIdentifier.class
                        || Collection.class.isAssignableFrom(field.getType()));
    }

    private static PartyIdentifier restore(
            PartyIdentifierStatus status,
            LocalDate expiresOn,
            boolean primary,
            Instant verifiedAt,
            String verifiedBy) {
        return PartyIdentifier.builder()
                .identifierId(IDENTIFIER_ID)
                .tenantId(TENANT_ID)
                .partyId(PARTY_ID)
                .identifierSchemeId(SCHEME_ID)
                .protectedValue(protectedValue())
                .issuerCode("AUTHORITY")
                .issuedOn(ISSUED_ON)
                .expiresOn(expiresOn)
                .primary(primary)
                .status(status)
                .verifiedAt(verifiedAt)
                .verifiedBy(verifiedBy)
                .version(new PartyIdentifierVersion(3))
                .auditInfo(AuditInfo.initial(CREATED_AT, "creator"))
                .build();
    }

    private static ProtectedIdentifierValue protectedValue() {
        return new ProtectedIdentifierValue(
                "v1.ciphertext",
                1,
                "0123456789abcdef".repeat(4),
                "******1234",
                new IdentifierRuleVersion(1));
    }

    private static void assertViolation(DomainViolation violation, Runnable action) {
        DomainValidationException failure = assertThrows(DomainValidationException.class, action::run);
        assertEquals(violation, failure.violation());
    }
}
