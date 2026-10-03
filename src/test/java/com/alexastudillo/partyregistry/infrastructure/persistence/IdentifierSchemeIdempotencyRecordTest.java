package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Verifies exact replay identity and detached JSON snapshot ownership before codec interpretation. */
class IdentifierSchemeIdempotencyRecordTest {

    @Test
    void identityIncludesExactTenantOperationAndKeyAndSupportsSerialization() throws Exception {
        UUID tenant = UUID.randomUUID();
        var id = new IdentifierSchemeIdempotencyRecordId(tenant, "identifier-scheme.create.v1", " Exact-Key ");
        var equivalent = new IdentifierSchemeIdempotencyRecordId(tenant, id.operation(), id.idempotencyKey());
        assertEquals(id, equivalent);
        assertEquals(id.hashCode(), equivalent.hashCode());
        var identities = new HashSet<IdentifierSchemeIdempotencyRecordId>();
        identities.add(id);
        identities.add(equivalent);
        identities.add(new IdentifierSchemeIdempotencyRecordId(UUID.randomUUID(), id.operation(), id.idempotencyKey()));
        identities.add(new IdentifierSchemeIdempotencyRecordId(tenant, "identifier-scheme.activate.v1", id.idempotencyKey()));
        identities.add(new IdentifierSchemeIdempotencyRecordId(tenant, id.operation(), "Exact-Key"));
        identities.add(new IdentifierSchemeIdempotencyRecordId(tenant, id.operation(), " exact-key "));
        assertEquals(5, identities.size());
        assertNotEquals(new IdentifierSchemeIdempotencyRecordId(UUID.randomUUID(), id.operation(), id.idempotencyKey()), id);
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(id);
        }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(id, input.readObject());
        }
    }

    @Test
    void retainsScopeTargetDigestVersionAndOriginalAttributionWithoutAliasingJson() {
        var id = new IdentifierSchemeIdempotencyRecordId(UUID.randomUUID(), "identifier-scheme.retire.v1", "key");
        UUID scheme = UUID.randomUUID();
        Instant acceptedAt = Instant.parse("2026-10-01T12:00:00.123456Z");
        var snapshot = JsonNodeFactory.instance.objectNode();
        snapshot.putObject("result").put("version", Long.MAX_VALUE).put("createdBy", "original-creator");
        var expected = snapshot.deepCopy();
        var entity = new IdentifierSchemeIdempotencyRecordEntity(id, "a".repeat(64), scheme,
                (short) 1, snapshot, acceptedAt, "original-actor");
        snapshot.withObject("result").put("version", 0);
        entity.resultSnapshot().withObject("result").put("createdBy", "changed");
        assertEquals(expected, entity.resultSnapshot());
        assertEquals(id, entity.id());
        assertEquals("a".repeat(64), entity.requestHash());
        assertEquals(scheme, entity.identifierSchemeId());
        assertEquals(1, entity.resultSnapshotSchemaVersion());
        assertEquals(acceptedAt, entity.createdAt());
        assertEquals("original-actor", entity.createdBy());
    }
}
