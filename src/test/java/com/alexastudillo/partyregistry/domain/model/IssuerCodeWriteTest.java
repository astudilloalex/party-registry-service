package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.testing.PartyIdentifierFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies that issuer canonicalization affects creation/supplied corrections while leaving restored history intact. */
class IssuerCodeWriteTest {

    @Test
    void creationCanonicalizesEntityCodesAndClearsBoundedBlankInput() {
        assertEquals("CIVIL_REGISTRY", create("civil_registry").issuerCode());
        assertNull(create(" ").issuerCode());
        assertNull(create("").issuerCode());
        assertNull(create(null).issuerCode());
        String invalid = "CIVIL__REGISTRY";
        assertThrows(DomainValidationException.class, () -> create(invalid));
    }

    @Test
    void suppliedCorrectionCanonicalizesAndBlankClears() {
        var current = PartyIdentifierFixtures.identifier(PartyType.NATURAL_PERSON,
                PartyIdentifierStatus.VERIFIED, true, 3, PartyIdentifierFixtures.EVALUATED_ON);
        var canonical = current.correctMetadata(FieldUpdate.present("civil_registry"), FieldUpdate.absent(), FieldUpdate.absent(),
                PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
        assertEquals("CIVIL_REGISTRY", canonical.issuerCode());
        var cleared = current.correctMetadata(FieldUpdate.present("\t "), FieldUpdate.absent(), FieldUpdate.absent(),
                PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
        assertNull(cleared.issuerCode());
        assertEquals(4, cleared.version().value());
    }

    @Test
    void omittedHistoricalIssuerAndVerificationDoNotReadmitOrNormalizeStoredText() {
        var legacy = restored("legacy-authority");
        assertEquals("legacy-authority", legacy.issuerCode());
        var corrected = legacy.correctMetadata(FieldUpdate.absent(), FieldUpdate.present(null), FieldUpdate.absent(),
                PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR);
        assertEquals("legacy-authority", corrected.issuerCode());
        assertEquals("legacy-authority", legacy.verify(PartyIdentifierFixtures.OCCURRED_AT, PartyIdentifierFixtures.ACTOR).issuerCode());
    }

    private static PartyIdentifier create(String issuer) {
        return base(issuer).created(PartyIdentifierFixtures.CREATED_AT, PartyIdentifierFixtures.CREATOR).build();
    }

    private static PartyIdentifier restored(String issuer) {
        return base(issuer).status(PartyIdentifierStatus.PENDING_VERIFICATION).version(new PartyIdentifierVersion(3))
                .auditInfo(AuditInfo.initial(PartyIdentifierFixtures.CREATED_AT, PartyIdentifierFixtures.CREATOR)).build();
    }

    private static PartyIdentifier.Builder base(String issuer) {
        var owner = PartyIdentifierFixtures.owner(PartyType.NATURAL_PERSON);
        return PartyIdentifier.builder()
                .identifierId(new PartyIdentifierId(java.util.UUID.fromString("0198d15c-0557-7a16-a6a8-e3f0ddb5b744")))
                .partyId(owner.partyId()).tenantId(owner.tenantId()).identifierSchemeId(PartyIdentifierFixtures.SCHEME)
                .protectedValue(PartyIdentifierFixtures.protectedValue()).issuerCode(issuer)
                .issuedOn(PartyIdentifierFixtures.EVALUATED_ON.minusYears(1)).expiresOn(PartyIdentifierFixtures.EVALUATED_ON);
    }
}
