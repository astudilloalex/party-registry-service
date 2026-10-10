package com.alexastudillo.partyregistry.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Verifies distinct two-integer lock domains and unambiguous exact tenant/key and owner/scheme hashing. */
class PartyIdentifierMutationLocksTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void separatesNamespacesAndEveryReplayTupleComponent() {
        var identity = new PartyIdentifierIdempotencyRecordId(TENANT, "party.identifier.verify.v1", " Exact-\ud83d\ude00 ");
        int hash = PartyIdentifierMutationLocks.replayLockId(identity);
        assertEquals(hash, PartyIdentifierMutationLocks.replayLockId(identity));
        assertNotEquals(PartyIdentifierMutationLocks.REPLAY_NAMESPACE, PartyIdentifierMutationLocks.SCOPE_NAMESPACE);
        assertNotEquals(hash, PartyIdentifierMutationLocks.replayLockId(new PartyIdentifierIdempotencyRecordId(UUID.randomUUID(), identity.operation(), identity.idempotencyKey())));
        assertNotEquals(hash, PartyIdentifierMutationLocks.replayLockId(new PartyIdentifierIdempotencyRecordId(TENANT, "party.identifier.reject.v1", identity.idempotencyKey())));
        assertNotEquals(hash, PartyIdentifierMutationLocks.replayLockId(new PartyIdentifierIdempotencyRecordId(TENANT, identity.operation(), "Exact-\ud83d\ude00")));
    }

    @Test
    void retainsExactCodeUnitsAndLengthDelimitedTupleBoundaries() {
        assertNotEquals(PartyIdentifierMutationLocks.replayLockId(new PartyIdentifierIdempotencyRecordId(TENANT, "ab", "c")),
                PartyIdentifierMutationLocks.replayLockId(new PartyIdentifierIdempotencyRecordId(TENANT, "a", "bc")));
        assertNotEquals(PartyIdentifierMutationLocks.replayLockId(new PartyIdentifierIdempotencyRecordId(TENANT, "action", "\ud800")),
                PartyIdentifierMutationLocks.replayLockId(new PartyIdentifierIdempotencyRecordId(TENANT, "action", "\ud801")));
    }

    @Test
    void scopesEveryOwnerDimension() {
        var scope = new PartyIdentifierMutationLocks.Scope(TENANT, UUID.randomUUID(), UUID.randomUUID());
        int hash = PartyIdentifierMutationLocks.scopeLockId(scope);
        assertEquals(hash, PartyIdentifierMutationLocks.scopeLockId(scope));
        assertNotEquals(hash, PartyIdentifierMutationLocks.scopeLockId(new PartyIdentifierMutationLocks.Scope(UUID.randomUUID(), scope.partyId(), scope.schemeId())));
        assertNotEquals(hash, PartyIdentifierMutationLocks.scopeLockId(new PartyIdentifierMutationLocks.Scope(TENANT, UUID.randomUUID(), scope.schemeId())));
        assertNotEquals(hash, PartyIdentifierMutationLocks.scopeLockId(new PartyIdentifierMutationLocks.Scope(TENANT, scope.partyId(), UUID.randomUUID())));
    }
}
