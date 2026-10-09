package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionAudit;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierCreatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Checks accepted write/audit/completion/event coherence without selecting lifecycle or replacement policy. */
final class PartyIdentifierMutationJournal {
    private final RequestMetadata metadata;
    private final Map<PartyIdentifierId, AcceptedWrite> accepted = new HashMap<>();
    private final Set<PartyIdentifierId> audited = new HashSet<>();
    private final Set<PartyIdentifierId> evented = new HashSet<>();

    PartyIdentifierMutationJournal(RequestMetadata metadata) { this.metadata = metadata; }

    void requireNewWrite(PartyIdentifier candidate) {
        if (accepted.size() == 2 || accepted.containsKey(candidate.identifierId())
                || !candidate.auditInfo().updatedBy().equals(metadata.userId())
                || accepted.values().stream().anyMatch(write -> !write.result().updatedAt().equals(candidate.auditInfo().updatedAt()))) {
            throw new IllegalStateException("Identifier writes must be unique and share accepted attribution");
        }
    }

    void recordAcceptedWrite(@Nullable PartyIdentifier source, PartyIdentifierResult result) {
        accepted.put(result.identifierId(), new AcceptedWrite(source, result));
    }

    void requireAudit(PartyIdentifierActionAudit audit) {
        var write = requireAccepted(audit.identifierId());
        PartyIdentifierResult result = write.result();
        var expectedState = audit.operation() == PartyIdentifierOperation.REJECT ? PartyIdentifierStatus.REJECTED : PartyIdentifierStatus.REVOKED;
        if (!metadata.equals(audit.requestMetadata()) || audited.contains(audit.identifierId())
                || !result.partyId().equals(audit.partyId()) || !result.version().equals(audit.resultingVersion())
                || !result.updatedAt().equals(audit.occurredAt()) || result.status() != expectedState) {
            throw new IllegalStateException("Withdrawal audit must match the accepted identifier revision");
        }
    }

    void recordAudit(PartyIdentifierId id) { audited.add(id); }

    void requireCompletion(CompletedPartyIdentifierMutation completion) {
        var write = requireAccepted(completion.result().identifierId());
        var source = write.source();
        PartyIdentifierVersion expected = source == null ? null : source.version();
        if (!metadata.tenantId().equals(completion.tenantId()) || !metadata.userId().equals(completion.acceptedBy())
                || !write.result().equals(completion.result()) || !write.result().updatedAt().equals(completion.acceptedAt())
                || !Objects.equals(expected, completion.expectedVersion()) || !matchesOperation(write, completion.operation())) {
            throw new IllegalStateException("Completion must match the accepted result and its original version");
        }
    }

    PartyIdentifierId requireEvent(OutboxEventCandidate event) {
        if (!metadata.tenantId().equals(event.tenantId()) || !metadata.processId().equals(event.correlationId())
                || !metadata.userId().equals(event.createdBy())) {
            throw new IllegalStateException("Identifier event must use accepted request attribution");
        }
        PartyIdentifierId id = switch (event) {
            case PartyIdentifierCreatedOutboxCandidate created -> created.identifierId();
            case PartyIdentifierChangedOutboxCandidate changed -> changed.identifierId();
            default -> throw new IllegalStateException("Mutation event must identify an identifier");
        };
        var write = requireAccepted(id);
        var result = write.result();
        boolean matches = switch (event) {
            case PartyIdentifierCreatedOutboxCandidate created -> write.source() == null && result.partyId().equals(created.partyId())
                    && result.version().equals(created.identifierVersion()) && result.schemeCode().equals(created.schemeCode()) && result.status() == created.status();
            case PartyIdentifierChangedOutboxCandidate changed -> write.source() != null && result.partyId().equals(changed.partyId())
                    && result.version().equals(changed.identifierVersion()) && result.schemeCode().equals(changed.schemeCode()) && result.status() == changed.status()
                    && result.isPrimary() == changed.isPrimary() && matchesKind(write, changed.kind());
            default -> false;
        };
        if (!matches || evented.contains(id) || !result.updatedAt().equals(event.occurredAt())) {
            throw new IllegalStateException("Identifier event must match one accepted revision and effect kind");
        }
        return id;
    }

    void recordEvent(PartyIdentifierId id) { evented.add(id); }

    boolean matchesOutcome(PartyIdentifierResult result) {
        var write = accepted.get(result.identifierId());
        return write != null && write.result().equals(result) && evented.equals(accepted.keySet())
                && accepted.values().stream().allMatch(this::hasRequiredAudit);
    }

    boolean isEmpty() { return accepted.isEmpty() && audited.isEmpty() && evented.isEmpty(); }

    private boolean hasRequiredAudit(AcceptedWrite write) {
        var status = write.result().status();
        return status != PartyIdentifierStatus.REJECTED && status != PartyIdentifierStatus.REVOKED
                || audited.contains(write.result().identifierId());
    }

    private AcceptedWrite requireAccepted(PartyIdentifierId id) {
        return Objects.requireNonNull(accepted.get(id), "Persist the identifier before recording its effects");
    }

    private static boolean matchesOperation(AcceptedWrite write, PartyIdentifierOperation operation) {
        return switch (operation) {
            case CREATE -> write.source() == null;
            case VERIFY -> matchesKind(write, PartyIdentifierChangedOutboxCandidate.Kind.VERIFIED);
            case REJECT -> matchesKind(write, PartyIdentifierChangedOutboxCandidate.Kind.REJECTED);
            case REVOKE -> matchesKind(write, PartyIdentifierChangedOutboxCandidate.Kind.REVOKED);
            case SET_PRIMARY -> write.result().isPrimary() && matchesKind(write, PartyIdentifierChangedOutboxCandidate.Kind.PRIMARY_CHANGED);
        };
    }

    private static boolean matchesKind(AcceptedWrite write, PartyIdentifierChangedOutboxCandidate.Kind kind) {
        var source = write.source();
        if (source == null) { return false; }
        var result = write.result();
        return switch (kind) {
            case UPDATED -> result.status() == source.status() && result.isPrimary() == source.isPrimary();
            case VERIFIED -> source.status() == PartyIdentifierStatus.PENDING_VERIFICATION && result.status() == PartyIdentifierStatus.VERIFIED;
            case REJECTED -> source.status() == PartyIdentifierStatus.PENDING_VERIFICATION && result.status() == PartyIdentifierStatus.REJECTED;
            case REVOKED -> source.status() == PartyIdentifierStatus.VERIFIED && result.status() == PartyIdentifierStatus.REVOKED;
            case PRIMARY_CHANGED -> source.status() == PartyIdentifierStatus.VERIFIED && result.status() == PartyIdentifierStatus.VERIFIED
                    && Objects.equals(source.issuerCode(), result.issuerCode()) && Objects.equals(source.issuedOn(), result.issuedOn())
                    && Objects.equals(source.expiresOn(), result.expiresOn());
        };
    }

    /** Retains the locked source and safe accepted result only until this transaction terminates. */
    private record AcceptedWrite(@Nullable PartyIdentifier source, PartyIdentifierResult result) { }
}
