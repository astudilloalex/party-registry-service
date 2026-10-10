package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.testing.PartyIdentifierFixtures;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies allowlisted schema-one historical completions and fail-closed authoritative row/snapshot coherence. */
class PartyIdentifierCompletionCodecTest {

    private final PartyIdentifierCompletionCodec codec = new PartyIdentifierCompletionCodec();

    @Test
    void roundTripsCreationAndEveryActionWithoutCurrentStateOrNormalization() {
        for (PartyIdentifierOperation operation : PartyIdentifierOperation.values()) {
            CompletedPartyIdentifierMutation completion = completion(operation);
            var identity = identity(operation);
            var snapshot = codec.encode(completion);
            var stored = new PartyIdentifierIdempotencyRecordEntity(identity, completion, snapshot);
            assertEquals(1, snapshot.schemaVersion());
            assertEquals(completion, codec.decode(stored));
            assertEquals("legacy-authority", codec.decode(stored).result().issuerCode());
        }
    }

    @Test
    void storesOnlySafeResultAndScopeMetadataWithoutRequestReasonKeyOrProtection() {
        var completion = completion(PartyIdentifierOperation.REVOKE);
        var snapshot = codec.encode(completion);
        assertEquals(Set.of("schemaVersion", "tenantId", "operation", "expectedVersion", "acceptedBy", "acceptedAt", "result"),
                snapshot.payload().properties().stream().map(java.util.Map.Entry::getKey).collect(Collectors.toSet()));
        for (String forbidden : List.of("requestFingerprint", "request", "idempotencyKey", "reasonCode", "reasonDetail",
                "encryptedValue", "normalizedValueHash", "encryptionKeyVersion", "normalizationVersion", "fixture.ciphertext")) {
            assertFalse(snapshot.payload().toString().contains(forbidden));
        }
    }

    @Test
    void rejectsUnknownSchemasFieldsIdsAndEnumsWithSanitizedCauseFreeFailures() {
        CompletedPartyIdentifierMutation completed = completion(PartyIdentifierOperation.REVOKE);
        for (Consumer<ObjectNode> corrupt : List.<Consumer<ObjectNode>>of(
                root -> root.put("schemaVersion", 2), root -> root.put("privateField", "sensitive-snapshot"),
                root -> root.put("operation", "unknown-operation"), root -> result(root).put("status", "UNKNOWN"),
                root -> result(root).put("identifierId", "not-uuid"), root -> result(root).put("partyId", UUID.randomUUID().toString()),
                root -> result(root).put("identifierSchemeId", "1-1-1-1-1"), root -> result(root).put("encryptedValue", "sensitive-snapshot"))) {
            ObjectNode root = (ObjectNode) codec.encode(completed).payload().deepCopy();
            corrupt.accept(root);
            var stored = new PartyIdentifierIdempotencyRecordEntity(identity(completed.operation()), completed, new IdempotencyResultSnapshot((short) 1, root));
            invalid(stored);
        }
        var unsupported = new PartyIdentifierIdempotencyRecordEntity(identity(completed.operation()), completed,
                new IdempotencyResultSnapshot((short) 2, codec.encode(completed).payload()));
        invalid(unsupported);
    }

    @Test
    void rejectsInconsistentAttributionVersionsTimestampsAndActionResults() {
        CompletedPartyIdentifierMutation completed = completion(PartyIdentifierOperation.REVOKE);
        for (Consumer<ObjectNode> corrupt : List.<Consumer<ObjectNode>>of(
                root -> root.put("tenantId", UUID.randomUUID().toString()), root -> root.put("acceptedBy", "different-actor"),
                root -> root.put("acceptedAt", PartyIdentifierFixtures.OCCURRED_AT.toString()),
                root -> root.put("expectedVersion", 1), root -> result(root).put("version", 99),
                root -> result(root).put("version", "8"), root -> result(root).put("updatedAt", "sensitive-snapshot"),
                root -> result(root).put("isPrimary", true), root -> result(root).put("status", "VERIFIED"),
                root -> result(root).put("verifiedAt", "invalid-time"))) {
            ObjectNode root = (ObjectNode) codec.encode(completed).payload().deepCopy();
            corrupt.accept(root);
            invalid(new PartyIdentifierIdempotencyRecordEntity(identity(completed.operation()), completed, new IdempotencyResultSnapshot((short) 1, root)));
        }
    }

    @Test
    void returnsDetachedPayloadTreesWithoutDirtyingAcceptedSnapshot() {
        var completed = completion(PartyIdentifierOperation.CREATE);
        var stored = new PartyIdentifierIdempotencyRecordEntity(identity(completed.operation()), completed, codec.encode(completed));
        ((ObjectNode) stored.resultSnapshot()).put("corrupt", true);
        assertEquals(completed, codec.decode(stored));
    }

    @Test
    void rejectsInvalidStoredResultDatesVerificationPairsAndTextBounds() {
        var completed = completion(PartyIdentifierOperation.REVOKE);
        for (Consumer<ObjectNode> corrupt : List.<Consumer<ObjectNode>>of(
                root -> result(root).put("expiresOn", PartyIdentifierFixtures.EVALUATED_ON.minusYears(2).toString()),
                root -> result(root).putNull("verifiedAt"), root -> result(root).putNull("verifiedBy"),
                root -> result(root).put("schemeCode", "s".repeat(65)), root -> result(root).put("maskedValue", "m".repeat(65)),
                root -> result(root).put("issuerCode", "i".repeat(65)), root -> result(root).remove("issuedOn"))) {
            ObjectNode root = (ObjectNode) codec.encode(completed).payload().deepCopy();
            corrupt.accept(root);
            invalid(new PartyIdentifierIdempotencyRecordEntity(identity(completed.operation()), completed, new IdempotencyResultSnapshot((short) 1, root)));
        }
    }

    @Test
    void rejectsCreationSnapshotsWithModificationOrVerificationHistory() {
        var completed = completion(PartyIdentifierOperation.CREATE);
        for (Consumer<ObjectNode> corrupt : List.<Consumer<ObjectNode>>of(
                root -> result(root).put("createdAt", PartyIdentifierFixtures.CREATED_AT.minusSeconds(1).toString()),
                root -> { result(root).put("verifiedAt", PartyIdentifierFixtures.CREATED_AT.toString());
                    result(root).put("verifiedBy", PartyIdentifierFixtures.CREATOR); })) {
            ObjectNode root = (ObjectNode) codec.encode(completed).payload().deepCopy();
            corrupt.accept(root);
            invalid(new PartyIdentifierIdempotencyRecordEntity(identity(completed.operation()), completed, new IdempotencyResultSnapshot((short) 1, root)));
        }
    }

    @Test
    void preservesHistoricalPastExpirationAndNullableMetadataWithoutEligibilityChecks() {
        var completed = completion(PartyIdentifierOperation.REVOKE);
        ObjectNode root = (ObjectNode) codec.encode(completed).payload().deepCopy();
        result(root).put("expiresOn", PartyIdentifierFixtures.EVALUATED_ON.minusDays(1).toString());
        result(root).putNull("issuedOn");
        result(root).putNull("issuerCode");
        var stored = new PartyIdentifierIdempotencyRecordEntity(identity(completed.operation()), completed, new IdempotencyResultSnapshot((short) 1, root));
        var restored = codec.decode(stored).result();
        assertEquals(PartyIdentifierFixtures.EVALUATED_ON.minusDays(1), restored.expiresOn());
        assertNull(restored.issuedOn());
        assertNull(restored.issuerCode());
        assertEquals(completed.result().verifiedAt(), restored.verifiedAt());
        assertEquals(completed.result().version(), restored.version());
    }

    private void invalid(PartyIdentifierIdempotencyRecordEntity stored) {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> codec.decode(stored));
        assertEquals("Stored identifier completion is invalid", failure.getMessage());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("sensitive-snapshot"));
    }

    private static ObjectNode result(ObjectNode root) {
        return (ObjectNode) root.path("result");
    }

    private static PartyIdentifierIdempotencyRecordId identity(PartyIdentifierOperation operation) {
        return new PartyIdentifierIdempotencyRecordId(PartyIdentifierFixtures.TENANT.value(), operation.namespace(), "exact-key");
    }

    private static CompletedPartyIdentifierMutation completion(PartyIdentifierOperation operation) {
        PartyIdentifierStatus status = switch (operation) {
            case CREATE -> PartyIdentifierStatus.PENDING_VERIFICATION;
            case VERIFY, SET_PRIMARY -> PartyIdentifierStatus.VERIFIED;
            case REJECT -> PartyIdentifierStatus.REJECTED;
            case REVOKE -> PartyIdentifierStatus.REVOKED;
        };
        long version = operation == PartyIdentifierOperation.CREATE ? 0 : 8;
        var base = PartyIdentifierFixtures.result(PartyIdentifierFixtures.identifier(PartyType.NATURAL_PERSON, status,
                operation == PartyIdentifierOperation.SET_PRIMARY, version, PartyIdentifierFixtures.EVALUATED_ON.plusDays(1)));
        var result = new PartyIdentifierResult(base.identifierId(), base.partyId(), base.identifierSchemeId(), base.schemeCode(),
                base.maskedValue(), base.status(), base.isPrimary(), "legacy-authority", base.issuedOn(), base.expiresOn(),
                base.verifiedAt(), base.verifiedBy(), base.version(), base.createdAt(), base.updatedAt());
        return new CompletedPartyIdentifierMutation(PartyIdentifierFixtures.TENANT, operation, "a".repeat(64), result,
                operation == PartyIdentifierOperation.CREATE ? null : new PartyIdentifierVersion(7), PartyIdentifierFixtures.CREATOR, base.updatedAt());
    }
}
