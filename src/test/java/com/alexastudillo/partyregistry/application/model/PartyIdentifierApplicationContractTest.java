package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.application.command.ChangePartyIdentifierLifecycleCommand;
import com.alexastudillo.partyregistry.application.command.InitialPartyIdentifierInput;
import com.alexastudillo.partyregistry.application.command.PatchPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.command.RegisterPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.command.SetPrimaryPartyIdentifierCommand;
import com.alexastudillo.partyregistry.application.query.GetPartyIdentifierQuery;
import com.alexastudillo.partyregistry.application.query.ListPartyIdentifiersQuery;
import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.testing.PartyIdentifierFixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins transport-neutral identifier request identities, independent versions and immutable safe page/outcome contracts. */
class PartyIdentifierApplicationContractTest {

    private static final String SECRET_VALUE = "private-document-value";
    private static final String SECRET_REASON = "private-withdrawal-detail";
    private static final String KEY = "private-idempotency-key";
    private final RequestMetadata metadata = new RequestMetadata(PartyIdentifierFixtures.TENANT, "operator", UUID.randomUUID());
    private final PartyIdentifierResult identifier = PartyIdentifierFixtures.result(PartyIdentifierFixtures.identifier(
            PartyType.NATURAL_PERSON, PartyIdentifierStatus.VERIFIED, false, 3, null));

    @Test
    void creationIdentityRetainsAllOriginalStringsWithoutActorProcessOrKey() {
        var raw = new InitialPartyIdentifierInput("SCHEME", SECRET_VALUE, "civil_registry", null, null, false);
        var canonical = new InitialPartyIdentifierInput("SCHEME", SECRET_VALUE, "CIVIL_REGISTRY", null, null, false);
        var original = new RegisterPartyIdentifierCommand(metadata, KEY, identifier.partyId(), raw).effectiveRequest();
        var changed = new RegisterPartyIdentifierCommand(metadata, KEY, identifier.partyId(), canonical).effectiveRequest();
        assertNotEquals(original, changed);
        assertEquals("civil_registry", original.issuerCode());
        var command = new RegisterPartyIdentifierCommand(metadata, KEY, identifier.partyId(), raw);
        assertEquals(original, command.effectiveRequest());
        assertFalse(command.toString().contains(SECRET_VALUE));
        assertFalse(command.toString().contains(KEY));
        assertFalse(original.toString().contains(SECRET_VALUE));
    }

    @Test
    void lifecycleEquivalenceRetainsExactReasonsAndExcludesRetryAttribution() {
        var reason = new PartyIdentifierReason("", SECRET_REASON);
        var first = lifecycle(metadata, KEY, reason);
        var retryMetadata = new RequestMetadata(metadata.tenantId(), "retry-operator", UUID.randomUUID());
        var retry = lifecycle(retryMetadata, "another-key", new PartyIdentifierReason("", SECRET_REASON));
        assertEquals(first.effectiveRequest(), retry.effectiveRequest());
        assertEquals(identifier.version(), first.expectedVersion());
        assertNotEquals(first.effectiveRequest(), lifecycle(metadata, KEY, PartyIdentifierReason.none()).effectiveRequest());
        assertFalse(first.toString().contains(SECRET_REASON));
        assertFalse(first.toString().contains(KEY));
        assertFalse(first.effectiveRequest().toString().contains(SECRET_REASON));
        assertFalse(reason.toString().contains(SECRET_REASON));
    }

    @Test
    void actionBodiesPreserveExactControlTextAndRejectImpossibleFieldCombinations() {
        var exact = new PartyIdentifierReason("\0", "\t " + SECRET_REASON);
        assertEquals("\0", exact.reasonCode());
        assertEquals("\t " + SECRET_REASON, exact.reasonDetail());
        assertNotEquals(PartyIdentifierReason.none(), new PartyIdentifierReason("", null));
        String oversized = "😀".repeat(65);
        assertThrows(IllegalArgumentException.class, () -> new PartyIdentifierReason(oversized, null));
        var party = identifier.partyId();
        var id = identifier.identifierId();
        var version = identifier.version();
        var none = PartyIdentifierReason.none();
        assertThrows(IllegalArgumentException.class, () -> new PartyIdentifierActionRequest(
                PartyIdentifierOperation.VERIFY, party, id, version, exact, false));
        assertThrows(IllegalArgumentException.class, () -> new PartyIdentifierActionRequest(
                PartyIdentifierOperation.REJECT, party, id, version, none, true));
        assertThrows(IllegalArgumentException.class, () -> new ChangePartyIdentifierLifecycleCommand(
                metadata, party, id, version, PartyIdentifierOperation.SET_PRIMARY, null, none));
        assertThrows(IllegalArgumentException.class, () -> new SetPrimaryPartyIdentifierCommand(
                metadata, party, id, version, " ", false));
    }

    @Test
    void effectivePrimaryDefaultAndActionNamespacesRemainIndependent() {
        var noReplacement = new SetPrimaryPartyIdentifierCommand(metadata, identifier.partyId(), identifier.identifierId(),
                identifier.version(), null, false);
        var explicitFalse = new SetPrimaryPartyIdentifierCommand(metadata, identifier.partyId(), identifier.identifierId(),
                identifier.version(), KEY, false);
        var replacement = new SetPrimaryPartyIdentifierCommand(metadata, identifier.partyId(), identifier.identifierId(),
                identifier.version(), KEY, true);
        assertEquals(noReplacement.effectiveRequest(), explicitFalse.effectiveRequest());
        assertNotEquals(noReplacement.effectiveRequest(), replacement.effectiveRequest());
        assertEquals("party.identifier.set-primary.v1", noReplacement.effectiveRequest().operation().namespace());
        assertEquals(5, java.util.Arrays.stream(PartyIdentifierOperation.values()).map(PartyIdentifierOperation::namespace).distinct().count());
        assertFalse(explicitFalse.toString().contains(KEY));
    }

    @Test
    void patchPreservesPresenceAndUsesOnlyTheIndependentIdentifierVersion() {
        var omission = new PatchPartyIdentifierCommand(metadata, identifier.partyId(), identifier.identifierId(),
                new PartyIdentifierVersion(7), FieldUpdate.absent(), FieldUpdate.present(null), FieldUpdate.absent());
        var clear = new PatchPartyIdentifierCommand(metadata, identifier.partyId(), identifier.identifierId(),
                identifier.version(), FieldUpdate.present(null), FieldUpdate.absent(), FieldUpdate.absent());
        assertFalse(omission.issuerCode().isPresent());
        assertTrue(clear.issuerCode().isPresent());
        assertEquals(7, omission.expectedVersion().value());
        assertEquals(identifier.version(), clear.expectedVersion());
    }

    @Test
    void queryFiltersPreserveExactSchemeAndAbsentVersusFalsePrimaryScope() {
        var absent = new PartyIdentifierSearchCriteria(null, "ExactCode", null, null, 50);
        var explicitFalse = new PartyIdentifierSearchCriteria(null, "ExactCode", null, false, 50);
        assertNotEquals(absent, explicitFalse);
        assertEquals("ExactCode", absent.schemeCode());
        assertEquals(50, PartyIdentifierSearchCriteria.DEFAULT_LIMIT);
        var scope = new PartyIdentifierSearchScope(metadata.tenantId(), identifier.partyId(), absent);
        assertNotEquals(scope, new PartyIdentifierSearchScope(metadata.tenantId(), identifier.partyId(), explicitFalse));
        assertEquals(scope, new ListPartyIdentifiersQuery(metadata, identifier.partyId(), absent, null).scope());
        assertEquals(identifier.identifierId(), new GetPartyIdentifierQuery(metadata, identifier.partyId(), identifier.identifierId()).identifierId());
        assertThrows(IllegalArgumentException.class, () -> new PartyIdentifierSearchCriteria(null, null, null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new PartyIdentifierSearchCriteria(null, null, null, null, 201));
    }

    @Test
    void pagePositionsPreserveTimestampPrecisionAndPageRowsCannotBeMutated() {
        var position = new PartyIdentifierPagePosition(PartyIdentifierFixtures.OCCURRED_AT, identifier.identifierId());
        var boundary = new PartyIdentifierPageBoundary(PartyIdentifierPageBoundary.Direction.PREVIOUS, position);
        assertEquals(PartyIdentifierFixtures.OCCURRED_AT, boundary.position().createdAt());
        var source = new ArrayList<>(List.of(identifier));
        var slice = new PartyIdentifierPageSlice(source, 5, Optional.of(position), Optional.empty());
        source.clear();
        assertEquals(List.of(identifier), slice.items());
        var immutableRows = slice.items();
        assertThrows(UnsupportedOperationException.class, immutableRows::clear);
        var page = new PartyIdentifierPageResult(slice.items(), "opaque-next", null, slice.totalElements(), 3);
        assertEquals(1, page.numberOfElements());
        assertEquals(5, page.totalElements());
        assertEquals(3, page.totalPages());
    }

    @Test
    void safeMutationOutcomesDistinguishApplicationFromReplayWithoutProtectedMaterial() {
        var applied = new PartyIdentifierMutationOutcome(identifier, PartyIdentifierMutationOutcome.Disposition.APPLIED);
        var replayed = new PartyIdentifierMutationOutcome(identifier, PartyIdentifierMutationOutcome.Disposition.REPLAYED);
        assertNotEquals(applied, replayed);
        assertEquals(applied.identifier(), replayed.identifier());
        assertFalse(applied.toString().contains("fixture.ciphertext"));
        for (String operation : List.of("APPLICATION_PARTY_IDENTIFIER_LIST", "APPLICATION_PARTY_IDENTIFIER_RETRIEVAL",
                "APPLICATION_PARTY_IDENTIFIER_PATCH", "APPLICATION_PARTY_IDENTIFIER_VERIFY", "APPLICATION_PARTY_IDENTIFIER_REJECT",
                "APPLICATION_PARTY_IDENTIFIER_REVOKE", "APPLICATION_PARTY_IDENTIFIER_SET_PRIMARY", "TRANSACTION_PARTY_IDENTIFIER_MUTATION")) {
            assertEquals(operation, ObservedOperation.valueOf(operation).name());
        }
    }

    private ChangePartyIdentifierLifecycleCommand lifecycle(RequestMetadata context, String key, PartyIdentifierReason reason) {
        return new ChangePartyIdentifierLifecycleCommand(context, identifier.partyId(), identifier.identifierId(),
                identifier.version(), PartyIdentifierOperation.REJECT, key, reason);
    }
}
