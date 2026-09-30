package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.application.command.ChangePartyLifecycleCommand;
import com.alexastudillo.partyregistry.application.command.PatchPartyCommand;
import com.alexastudillo.partyregistry.domain.model.AuditInfo;
import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.LegalEntityDetails;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.NaturalPersonDetails;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies effective lifecycle identity, historical result integrity, and transport-neutral root mutation inputs.
 */
class PartyMutationContractTest {

    private static final PartyId PARTY = new PartyId(UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab1"));
    private static final TenantId TENANT = new TenantId(UUID.fromString("0198ce2b-d6a3-7d6e-80ba-d97b21d793e5"));
    private static final Instant CREATED = LocalDate.of(2026, Month.SEPTEMBER, 19)
            .atStartOfDay().toInstant(ZoneOffset.UTC);

    @Test
    void effectiveIdentityIncludesActionTargetAndVersionButExcludesRetryAttribution() {
        var original = command("original-user", PARTY, 4, PartyLifecycleAction.ARCHIVE, " Key ");
        var retry = command("retry-user", PARTY, 4, PartyLifecycleAction.ARCHIVE, " Key ");
        var otherParty = new PartyId(UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab2"));

        assertNotEquals(original.requestMetadata(), retry.requestMetadata());
        assertEquals(original.effectiveRequest(), retry.effectiveRequest());
        assertEquals(Optional.of(" Key "), original.idempotencyKey());
        assertNotEquals(original.effectiveRequest(), command("original-user", otherParty, 4,
                PartyLifecycleAction.ARCHIVE, " Key ").effectiveRequest());
        assertNotEquals(original.effectiveRequest(), command("original-user", PARTY, 5,
                PartyLifecycleAction.ARCHIVE, " Key ").effectiveRequest());
        assertNotEquals(original.effectiveRequest(), command("original-user", PARTY, 4,
                PartyLifecycleAction.DEACTIVATE, " Key ").effectiveRequest());
    }

    @Test
    void anAbsentKeyIsValidAndRootPatchRetainsRawBlankInputForWorkflowPrecedence() {
        var metadata = metadata("operator");
        var version = new PartyVersion(4);
        var command = new ChangePartyLifecycleCommand(metadata, PARTY, version,
                PartyLifecycleAction.ARCHIVE, Optional.empty());
        var patch = new PatchPartyCommand(metadata, PARTY, version, " \t");

        assertEquals(Optional.empty(), command.idempotencyKey());
        assertEquals(" \t", patch.displayName());
        assertEquals(version, patch.expectedVersion());
    }

    @ParameterizedTest
    @EnumSource(PartyType.class)
    void appliedAndReplayedResultsRetainExactlyTheOriginalSafePartyData(PartyType type) {
        Party original = draft(type);
        PartyDetailsResult result = PartyDetailsResult.fromAggregate(original.archive(CREATED, "original-user"));
        var completed = new CompletedPartyLifecycle(
                new PartyLifecycleRequest(PartyLifecycleAction.ARCHIVE, PARTY, original.version()), result);
        var applied = new PartyMutationOutcome(result, PartyMutationOutcome.Disposition.APPLIED);
        var replayed = new PartyMutationOutcome(completed.result(), PartyMutationOutcome.Disposition.REPLAYED);

        assertSame(applied.party(), replayed.party());
        assertEquals("original-user", replayed.party().updatedBy());
        assertEquals(CREATED, replayed.party().updatedAt());
        assertEquals(5, replayed.party().version().value());
        assertNotEquals(applied.disposition(), replayed.disposition());
    }

    @Test
    void completedResultsRejectDifferentTargetsVersionsAndActionOutcomes() {
        PartyDetailsResult result = PartyDetailsResult.fromAggregate(draft(PartyType.NATURAL_PERSON)
                .archive(CREATED, "original-user"));
        var otherParty = new PartyId(UUID.fromString("0198ce2a-7b7d-7ab4-a5cf-4d4d7db89ab2"));
        var wrongTarget = new PartyLifecycleRequest(PartyLifecycleAction.ARCHIVE, otherParty, new PartyVersion(4));
        var wrongVersion = new PartyLifecycleRequest(PartyLifecycleAction.ARCHIVE, PARTY, new PartyVersion(3));
        var wrongAction = new PartyLifecycleRequest(PartyLifecycleAction.ACTIVATE, PARTY, new PartyVersion(4));

        assertThrows(IllegalArgumentException.class, () -> new CompletedPartyLifecycle(wrongTarget, result));
        assertThrows(IllegalArgumentException.class, () -> new CompletedPartyLifecycle(wrongVersion, result));
        assertThrows(IllegalArgumentException.class, () -> new CompletedPartyLifecycle(wrongAction, result));
    }

    private static ChangePartyLifecycleCommand command(
            String actor, PartyId partyId, long version, PartyLifecycleAction action, String key) {
        return new ChangePartyLifecycleCommand(metadata(actor), partyId, new PartyVersion(version), action, Optional.of(key));
    }

    private static RequestMetadata metadata(String actor) {
        return new RequestMetadata(TENANT, actor, UUID.randomUUID());
    }

    private static Party draft(PartyType type) {
        var audit = AuditInfo.initial(CREATED, "creator");
        var version = new PartyVersion(4);
        return switch (type) {
            case NATURAL_PERSON -> NaturalPerson.restore(PARTY, TENANT, "Historical Name", PartyRecordStatus.DRAFT,
                    version, audit, new NaturalPersonDetails("Historical", "Name", null, null, null, "GB"));
            case LEGAL_ENTITY -> LegalEntity.restore(PARTY, TENANT, "Historical Company", PartyRecordStatus.DRAFT,
                    version, audit, new LegalEntityDetails("Historical Company", null, null, "GB", null, null));
        };
    }
}
