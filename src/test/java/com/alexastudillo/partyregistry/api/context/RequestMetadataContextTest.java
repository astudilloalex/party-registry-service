package com.alexastudillo.partyregistry.api.context;

import com.alexastudillo.partyregistry.application.model.NationalityMutationOutcome;
import com.alexastudillo.partyregistry.application.model.NationalityResult;
import com.alexastudillo.partyregistry.application.model.NaturalPersonResult;
import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyRegistrationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.NationalityId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests verifying request metadata context state tracking and outcome recording.
 */
class RequestMetadataContextTest {

    @Test
    void tracksLifecycleAndOutcomesCorrectly() {
        RequestMetadataContext context = new RequestMetadataContext();

        assertNull(context.method());
        assertNull(context.path());
        assertNull(context.acceptedProcessId());
        assertFalse(context.isMdcInitialized());
        assertFalse(context.nationalityKeyed());
        assertNull(context.idempotencyOutcome());
        assertNull(context.mutationDisposition());
        assertNull(context.nationalityDisposition());
        assertThrows(IllegalStateException.class, context::metadata);

        context.start("POST", "/v1/parties");
        assertEquals("POST", context.method());
        assertEquals("/v1/parties", context.path());
        assertTrue(context.startedAtNanos() > 0);

        context.acceptProcessId("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1");
        assertEquals("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1", context.acceptedProcessId());

        context.markMdcInitialized();
        assertTrue(context.isMdcInitialized());

        PartyId partyId = new PartyId(UUID.randomUUID());
        TenantId tenantId = new TenantId(UUID.randomUUID());

        RequestMetadata metadata = new RequestMetadata(tenantId, "user-1", UUID.randomUUID());
        context.initialize(metadata);
        assertEquals(metadata, context.metadata());

        context.recordIdempotencyOutcome(PartyRegistrationOutcome.CREATED);
        assertEquals(PartyRegistrationOutcome.CREATED, context.idempotencyOutcome());

        NaturalPersonResult personResult = new NaturalPersonResult(
                partyId, tenantId, PartyType.NATURAL_PERSON, "Ada Lovelace",
                PartyRecordStatus.ACTIVE, new PartyVersion(1), "Ada", "Lovelace",
                null, null, null, null, Instant.now(), "user-1", Instant.now(), "user-1");
        PartyMutationOutcome partyOutcome = new PartyMutationOutcome(
                personResult, PartyMutationOutcome.Disposition.APPLIED);
        context.recordMutationDisposition(partyOutcome);
        assertEquals(PartyMutationOutcome.Disposition.APPLIED, context.mutationDisposition());

        context.markNationalityKeyed();
        assertTrue(context.nationalityKeyed());

        NationalityResult nationalityResult = new NationalityResult(
                new NationalityId(UUID.randomUUID()), partyId, "EC", true,
                null, null, Instant.now(), Instant.now());
        NationalityMutationOutcome nationalityOutcome = new NationalityMutationOutcome(
                nationalityResult, NationalityMutationOutcome.Disposition.APPLIED);
        context.recordNationalityDisposition(nationalityOutcome);
        assertEquals(NationalityMutationOutcome.Disposition.APPLIED, context.nationalityDisposition());
    }

    @Test
    void rejectsNullOutcomesWithExpectedMessage() {
        RequestMetadataContext context = new RequestMetadataContext();

        NullPointerException idempotencyEx = assertThrows(
                NullPointerException.class,
                () -> context.recordIdempotencyOutcome(null));
        assertEquals("outcome", idempotencyEx.getMessage());

        NullPointerException mutationEx = assertThrows(
                NullPointerException.class,
                () -> context.recordMutationDisposition(null));
        assertEquals("outcome", mutationEx.getMessage());

        NullPointerException nationalityEx = assertThrows(
                NullPointerException.class,
                () -> context.recordNationalityDisposition(null));
        assertEquals("outcome", nationalityEx.getMessage());
    }
}
