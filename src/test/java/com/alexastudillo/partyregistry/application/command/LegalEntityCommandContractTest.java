package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.LegalEntityPatch;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies trusted command identity, deferred semantic validation and legal
 * absence failures.
 */
class LegalEntityCommandContractTest {

    private static final RequestMetadata METADATA = new RequestMetadata(
            new TenantId(UUID.randomUUID()), "editor", UUID.randomUUID());
    private static final PartyId PARTY_ID = new PartyId(UUID.randomUUID());

    @Test
    void retainsTrustedMetadataAndPresenceWithoutMergingFields() {
        GetLegalEntityCommand get = new GetLegalEntityCommand(METADATA, PARTY_ID);
        LegalEntityPatch patch = new LegalEntityPatch(
                FieldUpdate.absent(), FieldUpdate.present(null), FieldUpdate.absent(),
                FieldUpdate.absent(), FieldUpdate.absent(), FieldUpdate.absent());
        PatchLegalEntityCommand command = new PatchLegalEntityCommand(METADATA, PARTY_ID, new PartyVersion(2),
                patch);
        assertSame(METADATA, get.requestMetadata());
        assertSame(METADATA.tenantId(), get.tenantId());
        assertSame(METADATA.tenantId(), command.tenantId());
        assertSame(PARTY_ID, command.partyId());
        assertSame(patch, command.patch());
        assertTrue(command.patch().tradeName().isPresent());
        assertNull(command.patch().tradeName().value());
        assertFalse(command.patch().legalName().isPresent());
    }

    @Test
    void replacementDefersDateValidationAndPreservesOriginalInput() {
        ReplaceLegalEntityCommand command = new ReplaceLegalEntityCommand(
                METADATA, PARTY_ID, PartyVersion.initial(), "  Name  ", null, null, " ec ",
                LocalDate.of(2021, Month.JANUARY, 1), LocalDate.of(2020, Month.JANUARY, 1));
        assertEquals("  Name  ", command.legalName());
        assertEquals(" ec ", command.incorporationCountryCode());
        assertSame(METADATA.tenantId(), command.tenantId());
        assertEquals(DomainViolation.DISSOLUTION_BEFORE_INCORPORATION,
                assertThrows(DomainValidationException.class, command::replacementDetails).violation());
    }

    @Test
    void legalAbsenceCarriesOnlyTheRequestedIdentityAndTenant() {
        TenantId tenantId = METADATA.tenantId();
        var failure = new ApplicationFailure.LegalEntityNotFound(PARTY_ID, tenantId);
        ApplicationException exception = new ApplicationException(failure);
        assertSame(failure, exception.failure());
        assertEquals("Legal entity not found", exception.getMessage());
        assertEquals(PARTY_ID, failure.partyId());
        assertEquals(tenantId, failure.tenantId());
        assertThrows(NullPointerException.class,
                () -> new ApplicationFailure.LegalEntityNotFound(null, tenantId));
        assertThrows(NullPointerException.class,
                () -> new ApplicationFailure.LegalEntityNotFound(PARTY_ID, null));
    }

    @Test
    void commandIdentityAndVersionAreMandatory() {
        LegalEntityPatch emptyPatch = LegalEntityPatch.empty();
        PartyVersion initialVersion = PartyVersion.initial();
        assertThrows(NullPointerException.class, () -> new GetLegalEntityCommand(null, PARTY_ID));
        assertThrows(NullPointerException.class, () -> new GetLegalEntityCommand(METADATA, null));
        assertThrows(NullPointerException.class,
                () -> new PatchLegalEntityCommand(METADATA, PARTY_ID, null, emptyPatch));
        assertThrows(NullPointerException.class,
                () -> new PatchLegalEntityCommand(METADATA, PARTY_ID, initialVersion, null));
        assertThrows(NullPointerException.class, () -> new ReplaceLegalEntityCommand(
                METADATA, PARTY_ID, null, "Name", null, null, "EC", null, null));
    }
}
