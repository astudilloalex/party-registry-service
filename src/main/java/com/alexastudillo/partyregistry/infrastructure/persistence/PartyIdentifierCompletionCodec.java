package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Validates schema-one immutable safe completion snapshots against authoritative persisted owner/operation/acceptance metadata. */
@ApplicationScoped
public class PartyIdentifierCompletionCodec {
    private static final short VERSION = 1;
    private static final String SCHEMA = "schemaVersion";
    private static final String TENANT = "tenantId";
    private static final String OPERATION = "operation";
    private static final String EXPECTED = "expectedVersion";
    private static final String ACTOR = "acceptedBy";
    private static final String INSTANT = "acceptedAt";
    private static final String RESULT = "result";
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA, TENANT, OPERATION, EXPECTED, ACTOR, INSTANT, RESULT);

    IdempotencyResultSnapshot encode(CompletedPartyIdentifierMutation completed) {
        validateResult(completed);
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put(SCHEMA, VERSION);
        root.put(TENANT, completed.tenantId().value().toString());
        root.put(OPERATION, completed.operation().namespace());
        var expected = completed.expectedVersion();
        if (expected == null) { root.putNull(EXPECTED); } else { root.put(EXPECTED, expected.value()); }
        root.put(ACTOR, completed.acceptedBy());
        root.put(INSTANT, completed.acceptedAt().toString());
        root.set(RESULT, PartyIdentifierSnapshotFields.encode(completed.result()));
        return new IdempotencyResultSnapshot(VERSION, root);
    }

    /** Fails closed without a parser cause or snapshot-bearing message; never applies current eligibility or normalization. */
    CompletedPartyIdentifierMutation decode(PartyIdentifierIdempotencyRecordEntity stored) {
        try {
            JsonNode root = stored.resultSnapshot();
            PartyIdentifierSnapshotFields.fields(root, ROOT_FIELDS);
            if (stored.resultSnapshotSchemaVersion() != VERSION || PartyIdentifierSnapshotFields.number(root, SCHEMA) != VERSION) {
                throw PartyIdentifierSnapshotFields.invalid();
            }
            var operation = operation(PartyIdentifierSnapshotFields.text(root, OPERATION));
            if (!stored.id().operation().equals(operation.namespace())
                    || !stored.id().tenantId().equals(PartyIdentifierSnapshotFields.uuid(root, TENANT))
                    || !stored.createdBy().equals(PartyIdentifierSnapshotFields.text(root, ACTOR))
                    || !stored.createdAt().equals(Instant.parse(PartyIdentifierSnapshotFields.text(root, INSTANT)))) {
                throw PartyIdentifierSnapshotFields.invalid();
            }
            Long expected = root.path(EXPECTED).isNull() ? null : PartyIdentifierSnapshotFields.number(root, EXPECTED);
            if (!Objects.equals(expected, stored.expectedVersion())) { throw PartyIdentifierSnapshotFields.invalid(); }
            var result = PartyIdentifierSnapshotFields.decode(root.path(RESULT));
            if (!stored.partyId().equals(result.partyId().value()) || !stored.identifierId().equals(result.identifierId().value())
                    || !stored.createdAt().equals(result.updatedAt())) { throw PartyIdentifierSnapshotFields.invalid(); }
            var completed = new CompletedPartyIdentifierMutation(new TenantId(stored.id().tenantId()), operation, stored.requestHash(), result,
                    expected == null ? null : new PartyIdentifierVersion(expected), stored.createdBy(), stored.createdAt());
            validateResult(completed);
            return completed;
        } catch (RuntimeException _) {
            throw PartyIdentifierSnapshotFields.invalid();
        }
    }

    private static void validateResult(CompletedPartyIdentifierMutation completed) {
        var result = completed.result();
        PartyIdentifierStatus expected = switch (completed.operation()) {
            case CREATE -> PartyIdentifierStatus.PENDING_VERIFICATION;
            case VERIFY, SET_PRIMARY -> PartyIdentifierStatus.VERIFIED;
            case REJECT -> PartyIdentifierStatus.REJECTED;
            case REVOKE -> PartyIdentifierStatus.REVOKED;
        };
        if (result.status() != expected || !result.updatedAt().equals(completed.acceptedAt())) {
            throw PartyIdentifierSnapshotFields.invalid();
        }
        if (completed.operation() == PartyIdentifierOperation.CREATE
                && (!result.createdAt().equals(completed.acceptedAt()) || result.verifiedAt() != null || result.verifiedBy() != null)) {
            throw PartyIdentifierSnapshotFields.invalid();
        }
        if ((completed.operation() == PartyIdentifierOperation.REJECT || completed.operation() == PartyIdentifierOperation.REVOKE) && result.isPrimary()) {
            throw PartyIdentifierSnapshotFields.invalid();
        }
        if (completed.operation() == PartyIdentifierOperation.SET_PRIMARY && !result.isPrimary()) {
            throw PartyIdentifierSnapshotFields.invalid();
        }
        if (completed.operation() == PartyIdentifierOperation.VERIFY
                && (!completed.acceptedAt().equals(result.verifiedAt()) || !completed.acceptedBy().equals(result.verifiedBy()))) {
            throw PartyIdentifierSnapshotFields.invalid();
        }
    }

    private static PartyIdentifierOperation operation(String namespace) {
        for (PartyIdentifierOperation operation : PartyIdentifierOperation.values()) {
            if (operation.namespace().equals(namespace)) { return operation; }
        }
        throw PartyIdentifierSnapshotFields.invalid();
    }
}
