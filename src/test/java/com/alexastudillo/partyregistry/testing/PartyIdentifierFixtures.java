package com.alexastudillo.partyregistry.testing;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
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
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.ProtectedIdentifierValue;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.UUID;

/** Supplies deterministic unit-test owners and protected identifier history without production credentials. */
public final class PartyIdentifierFixtures {

    public static final LocalDate EVALUATED_ON = LocalDate.of(2026, Month.OCTOBER, 3);
    public static final Instant CREATED_AT = EVALUATED_ON.minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    public static final Instant OCCURRED_AT = EVALUATED_ON.atTime(12, 0).toInstant(ZoneOffset.UTC).plusNanos(123_456_000);
    public static final Clock CLOCK = Clock.fixed(OCCURRED_AT, ZoneOffset.UTC);
    public static final String CREATOR = "identifier-fixture-creator";
    public static final String ACTOR = "identifier-fixture-operator";
    public static final TenantId TENANT = new TenantId(UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));
    public static final IdentifierSchemeId SCHEME = new IdentifierSchemeId(UUID.fromString("0198d111-08f1-7e48-b291-399bbb9cd605"));
    private static final PartyId NATURAL_ID = new PartyId(UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
    private static final PartyId LEGAL_ID = new PartyId(UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab2"));

    private PartyIdentifierFixtures() {
    }

    /** Creates a draft owner of either supported type at the controlled creation instant. */
    public static Party owner(PartyType type) {
        return switch (type) {
            case NATURAL_PERSON -> NaturalPerson.create(NATURAL_ID, TENANT, null,
                    new NaturalPersonDetails("Sample", "Person", null, null, null, null),
                    EVALUATED_ON, CREATED_AT, CREATOR);
            case LEGAL_ENTITY -> LegalEntity.create(LEGAL_ID, TENANT, null,
                    new LegalEntityDetails("Sample Organization", null, null, "EC", null, null),
                    EVALUATED_ON, CREATED_AT, CREATOR);
        };
    }

    /** Restores controlled history; verified/revoked fixtures retain the original verification evidence. */
    public static PartyIdentifier identifier(PartyType type, PartyIdentifierStatus status,
            boolean primary, long version, @Nullable LocalDate expiresOn) {
        Party owner = owner(type);
        boolean verified = status == PartyIdentifierStatus.VERIFIED || status == PartyIdentifierStatus.REVOKED;
        return PartyIdentifier.builder()
                .identifierId(new PartyIdentifierId(UUID.fromString("0198d15c-0557-7a16-a6a8-e3f0ddb5b744")))
                .tenantId(owner.tenantId()).partyId(owner.partyId()).identifierSchemeId(SCHEME)
                .protectedValue(protectedValue()).issuerCode("FIXTURE_AUTHORITY")
                .issuedOn(EVALUATED_ON.minusYears(1)).expiresOn(expiresOn).primary(primary).status(status)
                .verifiedAt(verified ? CREATED_AT : null).verifiedBy(verified ? CREATOR : null)
                .version(new PartyIdentifierVersion(version)).auditInfo(AuditInfo.initial(CREATED_AT, CREATOR)).build();
    }

    /** Provides synthetic storage-shaped material solely for preservation tests, never a real encrypted document. */
    public static ProtectedIdentifierValue protectedValue() {
        return new ProtectedIdentifierValue("fixture.ciphertext", 1, "0123456789abcdef".repeat(4),
                "******1234", new IdentifierRuleVersion(1));
    }

    /** Supplies a detached safe port result without carrying protected material across the result boundary. */
    public static PartyIdentifierResult result(PartyIdentifier identifier) {
        return new PartyIdentifierResult(identifier.identifierId(), identifier.partyId(), identifier.identifierSchemeId(),
                "FIXTURE_SCHEME", identifier.protectedValue().maskedValue(), identifier.status(), identifier.isPrimary(),
                identifier.issuerCode(), identifier.issuedOn(), identifier.expiresOn(), identifier.verifiedAt(), identifier.verifiedBy(),
                identifier.version(), identifier.auditInfo().createdAt(), identifier.auditInfo().updatedAt());
    }
}
