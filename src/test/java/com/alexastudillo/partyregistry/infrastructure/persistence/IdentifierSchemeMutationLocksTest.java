package com.alexastudillo.partyregistry.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies bounded lock hashes retain versioned domain separation and full exact tuple encoding. */
class IdentifierSchemeMutationLocksTest {
    @Test
    void separatesTenantActionKeyCodeAndAmbiguousUnicodeTuples() {
        var tenant = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var id = new IdentifierSchemeIdempotencyRecordId(tenant, "identifier-scheme.activate.v1", "Exact-😀");
        long expected = IdentifierSchemeMutationLocks.replayLockId(id);
        assertEquals(expected, IdentifierSchemeMutationLocks.replayLockId(new IdentifierSchemeIdempotencyRecordId(tenant, id.operation(), id.idempotencyKey())));
        for (var different : new IdentifierSchemeIdempotencyRecordId[]{
                new IdentifierSchemeIdempotencyRecordId(UUID.fromString("22222222-2222-2222-2222-222222222222"), id.operation(), id.idempotencyKey()),
                new IdentifierSchemeIdempotencyRecordId(tenant, "identifier-scheme.retire.v1", id.idempotencyKey()),
                new IdentifierSchemeIdempotencyRecordId(tenant, id.operation(), "exact-😀"),
                new IdentifierSchemeIdempotencyRecordId(tenant, id.operation(), " Exact-😀 ")}) {
            assertNotEquals(expected, IdentifierSchemeMutationLocks.replayLockId(different));
        }
        assertNotEquals(expected, IdentifierSchemeMutationLocks.codeLockId(id.idempotencyKey()));
        assertNotEquals(IdentifierSchemeMutationLocks.replayLockId(new IdentifierSchemeIdempotencyRecordId(tenant, "ab", "c")),
                IdentifierSchemeMutationLocks.replayLockId(new IdentifierSchemeIdempotencyRecordId(tenant, "a", "bc")));
        assertNotEquals(IdentifierSchemeMutationLocks.codeLockId("Exact"), IdentifierSchemeMutationLocks.codeLockId("exact"));
        assertNotEquals(IdentifierSchemeMutationLocks.codeLockId("Exact"), IdentifierSchemeMutationLocks.codeLockId(" Exact "));
    }
}
