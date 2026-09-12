package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierCategory;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSubjectType;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies qualifying identifier semantics and immutable Party activation transitions.
 */
class PartyActivationPolicyTest {

    private static final PartyId PARTY_ID = new PartyId(
            UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
    private static final TenantId TENANT_ID = new TenantId(
            UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));
    private static final IdentifierSchemeId SCHEME_ID = new IdentifierSchemeId(
            UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd605"));
    private static final LocalDate EVALUATED_ON = LocalDate.of(2026, 8, 30);
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T10:00:00Z");
    private static final Instant ACTIVATED_AT = Instant.parse("2026-08-30T11:00:00Z");
    private static final PartyActivationPolicy POLICY = new PartyActivationPolicy();

    @Test
    void qualifyingVerifiedIdentifierActivatesAndIncrementsExactlyOnce() {
        NaturalPerson original = draftParty();
        PartyIdentifier identifier = identifier(
                PartyIdentifierStatus.VERIFIED,
                EVALUATED_ON,
                false);
        IdentifierScheme scheme = scheme(
                IdentifierSchemeStatus.ACTIVE,
                IdentifierSubjectType.NATURAL_PERSON);

        Party activated = POLICY.activate(
                original,
                List.of(new PartyIdentifierEvidence(identifier, scheme)),
                EVALUATED_ON,
                ACTIVATED_AT,
                "activator");

        assertEquals(PartyRecordStatus.ACTIVE, activated.recordStatus());
        assertEquals(original.version().next(), activated.version());
        assertEquals(ACTIVATED_AT, activated.auditInfo().updatedAt());
        assertEquals("activator", activated.auditInfo().updatedBy());
        assertEquals(PartyRecordStatus.DRAFT, original.recordStatus());
        assertEquals(PartyVersion.initial(), original.version());
        assertNotSame(original, activated);
        assertViolation(DomainViolation.PARTY_ACTIVATION_INVALID_STATE,
                () -> POLICY.activate(
                        activated,
                        List.of(new PartyIdentifierEvidence(identifier, scheme)),
                        EVALUATED_ON,
                        ACTIVATED_AT.plusSeconds(1),
                        "activator"));
    }

    @Test
    void pendingIdentifierDoesNotQualify() {
        assertIneligible(identifier(
                PartyIdentifierStatus.PENDING_VERIFICATION,
                EVALUATED_ON.plusDays(1),
                false), scheme(IdentifierSchemeStatus.ACTIVE, IdentifierSubjectType.NATURAL_PERSON));
    }

    @Test
    void expiredVerifiedIdentifierDoesNotQualify() {
        assertIneligible(identifier(
                PartyIdentifierStatus.VERIFIED,
                EVALUATED_ON.minusDays(1),
                false), scheme(IdentifierSchemeStatus.ACTIVE, IdentifierSubjectType.NATURAL_PERSON));
    }

    @Test
    void incompatibleVerifiedIdentifierDoesNotQualify() {
        assertIneligible(identifier(
                PartyIdentifierStatus.VERIFIED,
                EVALUATED_ON.plusDays(1),
                false), scheme(IdentifierSchemeStatus.ACTIVE, IdentifierSubjectType.LEGAL_ENTITY));
    }

    @Test
    void deprecatedAndRetiredSchemesRemainEligibleForActivation() {
        PartyIdentifier identifier = identifier(
                PartyIdentifierStatus.VERIFIED,
                EVALUATED_ON.plusDays(1),
                false);

        for (IdentifierSchemeStatus status : new IdentifierSchemeStatus[] {
                IdentifierSchemeStatus.DEPRECATED,
                IdentifierSchemeStatus.RETIRED }) {
            Party activated = POLICY.activate(
                    draftParty(),
                    List.of(new PartyIdentifierEvidence(
                            identifier,
                            scheme(status, IdentifierSubjectType.NATURAL_PERSON))),
                    EVALUATED_ON,
                    ACTIVATED_AT,
                    "activator");
            assertEquals(PartyRecordStatus.ACTIVE, activated.recordStatus());
        }
    }

    @Test
    void nonPrimaryIdentifierQualifiesForActivation() {
        PartyIdentifier identifier = identifier(
                PartyIdentifierStatus.VERIFIED,
                EVALUATED_ON.plusDays(1),
                false);

        Party activated = POLICY.activate(
                draftParty(),
                List.of(new PartyIdentifierEvidence(
                        identifier,
                        scheme(IdentifierSchemeStatus.ACTIVE, IdentifierSubjectType.NATURAL_PERSON))),
                EVALUATED_ON,
                ACTIVATED_AT,
                "activator");

        assertEquals(PartyRecordStatus.ACTIVE, activated.recordStatus());
    }

    @Test
    void compatibleVerifiedIdentifierActivatesLegalEntity() {
        LegalEntity legalEntity = LegalEntity.create(
                PARTY_ID,
                TENANT_ID,
                null,
                new LegalEntityDetails("Analytical Engines Ltd.", null, null, "GB", null, null),
                EVALUATED_ON,
                CREATED_AT,
                "creator");

        Party activated = POLICY.activate(
                legalEntity,
                List.of(new PartyIdentifierEvidence(
                        identifier(PartyIdentifierStatus.VERIFIED, EVALUATED_ON.plusDays(1), false),
                        scheme(IdentifierSchemeStatus.ACTIVE, IdentifierSubjectType.LEGAL_ENTITY))),
                EVALUATED_ON,
                ACTIVATED_AT,
                "activator");

        assertEquals(PartyType.LEGAL_ENTITY, activated.type());
        assertEquals(PartyRecordStatus.ACTIVE, activated.recordStatus());
        assertEquals(PartyVersion.initial().next(), activated.version());
    }

    @Test
    void invalidCurrentStatePrecedesMissingIdentifierEvidence() {
        NaturalPerson active = NaturalPerson.restore(
                PARTY_ID,
                TENANT_ID,
                "Ada Lovelace",
                PartyRecordStatus.ACTIVE,
                new PartyVersion(4),
                AuditInfo.initial(CREATED_AT, "creator"),
                new NaturalPersonDetails("Ada", "Lovelace", null, null, null, null));

        assertViolation(DomainViolation.PARTY_ACTIVATION_INVALID_STATE,
                () -> POLICY.activate(active, List.of(), EVALUATED_ON, ACTIVATED_AT, "activator"));
        assertEquals(PartyRecordStatus.ACTIVE, active.recordStatus());
        assertEquals(new PartyVersion(4), active.version());
    }

    @Test
    void identifierMustBelongToThePartyAndSelectedScheme() {
        PartyIdentifier otherPartyIdentifier = PartyIdentifier.restore(
                new PartyIdentifierId(UUID.fromString("0198d15c-0557-7a16-a6a8-e3f0ddb5b744")),
                TENANT_ID,
                new PartyId(UUID.fromString("0198d3e5-2fbf-7560-922e-5b586b245d1f")),
                SCHEME_ID,
                protectedValue(),
                null,
                null,
                EVALUATED_ON.plusDays(1),
                false,
                PartyIdentifierStatus.VERIFIED,
                CREATED_AT,
                "verifier",
                PartyIdentifierVersion.initial(),
                AuditInfo.initial(CREATED_AT, "creator"));

        assertIneligible(
                otherPartyIdentifier,
                scheme(IdentifierSchemeStatus.ACTIVE, IdentifierSubjectType.BOTH));
    }

    private static void assertIneligible(PartyIdentifier identifier, IdentifierScheme scheme) {
        NaturalPerson party = draftParty();
        assertViolation(DomainViolation.PARTY_ACTIVATION_IDENTIFIER_REQUIRED,
                () -> POLICY.activate(
                        party,
                        List.of(new PartyIdentifierEvidence(identifier, scheme)),
                        EVALUATED_ON,
                        ACTIVATED_AT,
                        "activator"));
        assertEquals(PartyRecordStatus.DRAFT, party.recordStatus());
        assertEquals(PartyVersion.initial(), party.version());
    }

    private static NaturalPerson draftParty() {
        return NaturalPerson.create(
                PARTY_ID,
                TENANT_ID,
                null,
                new NaturalPersonDetails("Ada", "Lovelace", null, null, null, null),
                EVALUATED_ON,
                CREATED_AT,
                "creator");
    }

    private static PartyIdentifier identifier(
            PartyIdentifierStatus status,
            LocalDate expiresOn,
            boolean primary) {
        Instant verifiedAt = status == PartyIdentifierStatus.VERIFIED ? CREATED_AT : null;
        String verifiedBy = status == PartyIdentifierStatus.VERIFIED ? "verifier" : null;
        return PartyIdentifier.restore(
                new PartyIdentifierId(UUID.fromString("0198d15c-0557-7a16-a6a8-e3f0ddb5b744")),
                TENANT_ID,
                PARTY_ID,
                SCHEME_ID,
                protectedValue(),
                null,
                null,
                expiresOn,
                primary,
                status,
                verifiedAt,
                verifiedBy,
                PartyIdentifierVersion.initial(),
                AuditInfo.initial(CREATED_AT, "creator"));
    }

    private static IdentifierScheme scheme(
            IdentifierSchemeStatus status,
            IdentifierSubjectType subjectType) {
        return new IdentifierScheme(
                SCHEME_ID,
                "GENERIC-ID",
                "EC",
                IdentifierCategory.OTHER,
                subjectType,
                "Generic official identifier",
                null,
                StandardIdentifierNormalizer.TRIM_UPPERCASE_V1.key(),
                StandardIdentifierValidator.ALPHANUMERIC_V1.key(),
                null,
                null,
                false,
                status,
                IdentifierSchemeVersion.initial(),
                AuditInfo.initial(CREATED_AT, "catalog-admin"));
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
