package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionAudit;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierOperation;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationContext;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Enforces one tenant, owner, replay key and primary scope on sequential same-session identifier capabilities. */
final class HibernateReactivePartyIdentifierMutationContext implements PartyIdentifierMutationContext {
    private final Mutiny.Session session;
    private final RequestMetadata metadata;
    private final PartyIdentifierMutationReader reader;
    private final PartyIdentifierMutationPersistence writes;
    private final PartyIdentifierMutationLocks locks;
    private final PartyIdentifierMutationEffects effects;
    private final PartyIdentifierMutationJournal journal;
    private final Context owner;
    private final Map<PartyIdentifierId, PartyIdentifier> locked = new HashMap<>();
    private boolean active = true;
    private boolean busy;
    private boolean failed;
    private boolean ownerRequested;
    private boolean partyPresent;
    private boolean replayResolved;
    private boolean recorded;
    private boolean schemeRequested;
    private @Nullable PartyId party;
    private @Nullable IdentifierSchemeId resolvedScheme;
    private PartyIdentifierMutationLocks.@Nullable Scope scope;
    private @Nullable IdentifierScheme registrationScheme;
    private @Nullable PartyIdentifierIdempotencyRecordId replayScope;
    private @Nullable CompletedPartyIdentifierMutation completed;
    private @Nullable CompletedPartyIdentifierMutation recordedCompletion;

    HibernateReactivePartyIdentifierMutationContext(Mutiny.Session session, RequestMetadata metadata, PartyIdentifierMutationReader reader,
            PartyIdentifierMutationPersistence writes, PartyIdentifierMutationLocks locks, PartyIdentifierMutationEffects effects) {
        this.session = session; this.metadata = metadata; this.reader = reader; this.writes = writes; this.locks = locks; this.effects = effects;
        journal = new PartyIdentifierMutationJournal(metadata);
        owner = Objects.requireNonNull(Vertx.currentContext(), "Reactive identifier context is required");
    }

    @Override
    public TenantId tenantId() { requireActive(); return metadata.tenantId(); }

    @Override
    public Uni<Void> serializeReplayKey(PartyIdentifierOperation operation, String key) {
        return run(() -> {
            if (ownerRequested || replayScope != null) { throw new IllegalStateException("Serialize one replay key before owner access"); }
            var identity = identity(operation, key);
            return locks.serializeReplayKey(session, identity).invoke(() -> replayScope = identity);
        });
    }

    @Override
    public Uni<Optional<CompletedPartyIdentifierMutation>> findCompleted(PartyIdentifierOperation operation, String key) {
        return run(() -> {
            var identity = requireReplay(operation, key);
            if (ownerRequested || replayResolved) { throw new IllegalStateException("Resolve replay once before owner access"); }
            return effects.findCompleted(session, identity).invoke(found -> { completed = found.orElse(null); replayResolved = true; });
        });
    }

    @Override
    public Uni<Optional<PartyType>> findOwnedParty(PartyId id) {
        return run(() -> {
            requireNewOperation();
            if (ownerRequested) { throw new IllegalStateException("Lock one tenant-qualified owner per operation"); }
            ownerRequested = true; party = id;
            return reader.findOwnedParty(session, metadata.tenantId().value(), id.value()).invoke(found -> partyPresent = found.isPresent());
        });
    }

    @Override
    public Uni<Optional<IdentifierSchemeId>> findIdentifierSchemeId(PartyId id, PartyIdentifierId identifier) {
        return run(() -> {
            requireSchemeResolution(id);
            return reader.findIdentifierSchemeId(session, metadata.tenantId().value(), id.value(), identifier.value())
                    .invoke(found -> resolvedScheme = found.orElse(null));
        });
    }

    @Override
    public Uni<Optional<IdentifierSchemeId>> findSchemeIdByCode(String code) {
        return run(() -> {
            requireSchemeResolution(Objects.requireNonNull(party, "Read the owner before scheme identity"));
            return reader.findSchemeIdByCode(session, code).invoke(found -> resolvedScheme = found.orElse(null));
        });
    }

    @Override
    public Uni<Void> serializeScope(PartyId id, IdentifierSchemeId scheme) {
        return run(() -> {
            requireOwned(id);
            if (scope != null || !scheme.equals(resolvedScheme)) { throw new IllegalStateException("Serialize the single resolved identifier scope"); }
            var requested = new PartyIdentifierMutationLocks.Scope(metadata.tenantId().value(), id.value(), scheme.value());
            return locks.serializeScope(session, requested).invoke(() -> scope = requested);
        });
    }

    @Override
    public Uni<Optional<IdentifierScheme>> findSchemeForRegistration(IdentifierSchemeId id) {
        return run(() -> {
            var bound = requireScope();
            if (!bound.schemeId().equals(id.value()) || registrationScheme != null || !locked.isEmpty()) {
                throw new IllegalStateException("Read the serialized creation scheme before child access");
            }
            return reader.findSchemeForRegistration(session, id.value()).invoke(found -> registrationScheme = found.orElse(null));
        });
    }

    @Override
    public Uni<Optional<PartyIdentifier>> findForUpdate(PartyId id, PartyIdentifierId identifier) {
        return run(() -> {
            requireOwned(id);
            var bound = requireScope();
            if (locked.containsKey(identifier) || locked.size() == 2 || registrationScheme != null) {
                throw new IllegalStateException("Lock each existing identifier once in the serialized scope");
            }
            return reader.findForUpdate(session, bound, identifier.value()).invoke(found -> found.ifPresent(value -> locked.put(identifier, value)));
        });
    }

    @Override
    public Uni<Optional<PartyIdentifier>> findCurrentPrimary(PartyId id, IdentifierSchemeId scheme) {
        return run(() -> {
            requireOwned(id);
            var bound = requireScope();
            if (!bound.schemeId().equals(scheme.value()) || registrationScheme != null || !journal.isEmpty()) {
                throw new IllegalStateException("Read the current primary in the serialized scope before writes");
            }
            return reader.findCurrentPrimary(session, bound).invoke(found -> found.ifPresent(value -> locked.putIfAbsent(value.identifierId(), value)));
        });
    }

    @Override
    public Uni<PartyIdentifierResult> insertIdentifier(PartyIdentifier candidate) {
        return run(() -> {
            requireCandidateScope(candidate);
            var scheme = Objects.requireNonNull(registrationScheme, "Lock the creation scheme before inserting");
            if (!scheme.id().equals(candidate.identifierSchemeId()) || !locked.isEmpty() || !journal.isEmpty()) {
                throw new IllegalStateException("Insert one candidate under its locked creation scheme");
            }
            journal.requireNewWrite(candidate);
            return writes.insertIdentifier(session, candidate).invoke(result -> journal.recordAcceptedWrite(null, result));
        });
    }

    @Override
    public Uni<PartyIdentifierResult> updateIdentifier(PartyIdentifier candidate, PartyIdentifierVersion expected) {
        return run(() -> {
            requireCandidateScope(candidate);
            var source = Objects.requireNonNull(locked.get(candidate.identifierId()), "Lock the target before writing");
            if (!source.version().equals(expected)) { throw new IllegalStateException("Write against the freshly locked identifier version"); }
            journal.requireNewWrite(candidate);
            return writes.updateIdentifier(session, candidate, expected).invoke(result -> journal.recordAcceptedWrite(source, result));
        });
    }

    @Override
    public Uni<Void> appendActionAudit(PartyIdentifierActionAudit audit) {
        return run(() -> {
            requireNewOperation(); journal.requireAudit(audit);
            return effects.appendAudit(session, audit).invoke(() -> journal.recordAudit(audit.identifierId()));
        });
    }

    @Override
    public Uni<Void> recordCompletion(PartyIdentifierOperation operation, String key, CompletedPartyIdentifierMutation completion) {
        return run(() -> {
            requireNewOperation();
            var identity = requireReplay(operation, key);
            if (recorded || completion.operation() != operation) { throw new IllegalStateException("Record one completion in its exact operation scope"); }
            journal.requireCompletion(completion);
            return effects.recordCompletion(session, identity, completion).invoke(() -> { recorded = true; recordedCompletion = completion; });
        });
    }

    @Override
    public Uni<Void> appendEnabledEvent(OutboxEventCandidate event) {
        return run(() -> {
            requireNewOperation();
            var id = journal.requireEvent(event);
            return effects.appendEvent(session, event).invoke(() -> journal.recordEvent(id));
        });
    }

    void verifyOutcome(PartyIdentifierMutationOutcome outcome) {
        requireActive();
        if (busy || outcome == null) { throw new IllegalStateException("Identifier workflow did not finish sequentially"); }
        var original = completed;
        var acceptedCompletion = recordedCompletion;
        boolean matches = switch (outcome.disposition()) {
            case APPLIED -> original == null && journal.matchesOutcome(outcome.identifier())
                    && (replayScope == null || recorded && acceptedCompletion != null && acceptedCompletion.result().equals(outcome.identifier()));
            case REPLAYED -> original != null && !ownerRequested && !recorded && journal.isEmpty() && original.result().equals(outcome.identifier());
        };
        if (!matches) { throw new IllegalStateException("Identifier outcome does not match its complete storage work"); }
    }

    void close() { active = false; }

    private <T> Uni<T> run(Supplier<Uni<T>> work) {
        return Uni.createFrom().deferred(() -> {
            requireActive();
            if (busy) { failed = true; throw new IllegalStateException("Identifier session operations must be sequential"); }
            busy = true;
            return Uni.createFrom().<T>deferred(work::get).onFailure().invoke(() -> failed = true).onTermination().invoke(() -> busy = false);
        });
    }

    private void requireActive() {
        if (!active || failed || !session.isOpen() || Vertx.currentContext() != owner) {
            throw new IllegalStateException("Identifier scope is closed, failed or used from another reactive context");
        }
    }

    private void requireNewOperation() {
        if (completed != null || replayScope != null && !replayResolved) { throw new IllegalStateException("Resolve completed replay before new-operation access"); }
    }

    private void requireOwned(PartyId id) {
        requireNewOperation();
        if (!partyPresent || !id.equals(party)) { throw new IllegalStateException("Use only the present tenant-qualified owner"); }
    }

    private void requireSchemeResolution(PartyId id) {
        requireOwned(id);
        if (schemeRequested || scope != null) { throw new IllegalStateException("Resolve one immutable scheme before scope serialization"); }
        schemeRequested = true;
    }

    private PartyIdentifierMutationLocks.Scope requireScope() {
        requireNewOperation();
        return Objects.requireNonNull(scope, "Serialize the identifier scope before child access");
    }

    private void requireCandidateScope(PartyIdentifier candidate) {
        var bound = requireScope();
        if (recorded || !bound.tenantId().equals(candidate.tenantId().value()) || !bound.partyId().equals(candidate.partyId().value())
                || !bound.schemeId().equals(candidate.identifierSchemeId().value())) {
            throw new IllegalStateException("Candidate must belong to the serialized tenant/owner/scheme");
        }
    }

    private PartyIdentifierIdempotencyRecordId requireReplay(PartyIdentifierOperation operation, String key) {
        var identity = identity(operation, key);
        if (!identity.equals(replayScope)) { throw new IllegalStateException("Replay access requires the complete serialized key tuple"); }
        return identity;
    }

    private PartyIdentifierIdempotencyRecordId identity(PartyIdentifierOperation operation, String key) {
        return new PartyIdentifierIdempotencyRecordId(metadata.tenantId().value(), operation.namespace(), key);
    }
}
