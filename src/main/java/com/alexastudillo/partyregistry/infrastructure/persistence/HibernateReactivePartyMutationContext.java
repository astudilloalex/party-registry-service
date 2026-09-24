package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.CompletedPartyLifecycle;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyActivatedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyChangedOutboxCandidate;
import com.alexastudillo.partyregistry.application.model.PartyDetailsResult;
import com.alexastudillo.partyregistry.application.model.PartyLifecycleAction;
import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyMutationContext;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import com.alexastudillo.partyregistry.domain.policy.PartyActivationEvidence;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Restricts mutation capabilities to one tenant, Vert.x context, key-before-root lock sequence, and active transaction. */
final class HibernateReactivePartyMutationContext implements PartyMutationContext {

    private final Mutiny.Session session;
    private final RequestMetadata metadata;
    private final PartyRootMutationPersistence roots;
    private final PartyLifecycleIdempotencyPersistence replay;
    private final PartyRootOutboxPersistence outbox;
    private final Context owner;
    private boolean active = true;
    private boolean busy;
    private boolean failed;
    private boolean rootRequested;
    private boolean replayResolved;
    private boolean recorded;
    private boolean appended;
    private @Nullable ReplayScope replayScope;
    private @Nullable CompletedPartyLifecycle completed;
    private @Nullable Party locked;
    private @Nullable Party stored;

    HibernateReactivePartyMutationContext(Mutiny.Session session, RequestMetadata metadata, PartyRootMutationPersistence roots,
            PartyLifecycleIdempotencyPersistence replay, PartyRootOutboxPersistence outbox) {
        this.session = session;
        this.metadata = metadata;
        this.roots = roots;
        this.replay = replay;
        this.outbox = outbox;
        owner = Objects.requireNonNull(Vertx.currentContext(), "Reactive mutation context is required");
    }

    @Override
    public TenantId tenantId() {
        requireActive();
        return metadata.tenantId();
    }

    @Override
    public Uni<Void> serializeReplayKey(PartyLifecycleAction action, String key) {
        return run(() -> {
            if (rootRequested || replayScope != null) {
                throw new IllegalStateException("Serialize one replay key before requesting the root lock");
            }
            replayScope = new ReplayScope(action, key);
            return replay.serializeReplayKey(session, metadata.tenantId(), action, key);
        });
    }

    @Override
    public Uni<Optional<CompletedPartyLifecycle>> findCompleted(PartyLifecycleAction action, String key) {
        return run(() -> {
            requireReplayScope(action, key);
            if (rootRequested) {
                throw new IllegalStateException("Resolve completed replay before requesting the root lock");
            }
            return replay.findCompleted(session, metadata.tenantId(), action, key).invoke(found -> {
                completed = found.orElse(null);
                replayResolved = true;
            });
        });
    }

    @Override
    public Uni<Optional<Party>> findForUpdate(PartyId partyId) {
        return run(() -> {
            if (rootRequested || replayScope != null && !replayResolved) {
                throw new IllegalStateException("Resolve replay before acquiring the single root lock");
            }
            rootRequested = true;
            return roots.findForUpdate(session, metadata.tenantId(), partyId).invoke(found -> locked = found.orElse(null));
        });
    }

    @Override
    public Uni<List<PartyActivationEvidence>> activationEvidence(PartyId partyId) {
        return run(() -> {
            Party source = requireLocked();
            if (!source.partyId().equals(partyId) || stored != null) {
                throw new IllegalStateException("Evidence must describe the locked root before writing");
            }
            return roots.activationEvidence(session, metadata.tenantId(), partyId);
        });
    }

    @Override
    public Uni<Party> persistRoot(Party candidate, PartyVersion expectedVersion) {
        return run(() -> {
            Party source = requireLocked();
            if (stored != null || !candidate.auditInfo().updatedBy().equals(metadata.userId())) {
                throw new IllegalStateException("Persist one root change attributed to the current request");
            }
            return roots.persistRoot(session, metadata.tenantId(), source, candidate, expectedVersion).invoke(result -> stored = result);
        });
    }

    @Override
    public Uni<Void> recordCompletion(PartyLifecycleAction action, String key, CompletedPartyLifecycle outcome) {
        return run(() -> {
            requireReplayScope(action, key);
            Party accepted = requireStored();
            if (recorded || !PartyDetailsResult.fromAggregate(accepted).equals(outcome.result())
                    || !requireLocked().version().equals(outcome.request().expectedVersion())) {
                throw new IllegalStateException("Record exactly the original accepted root result");
            }
            return replay.recordCompletion(session, metadata.tenantId(), action, key, outcome).invoke(() -> recorded = true);
        });
    }

    @Override
    public Uni<Void> appendEnabledEvent(OutboxEventCandidate event) {
        return run(() -> {
            Party accepted = requireStored();
            if (appended || !metadata.tenantId().equals(event.tenantId()) || !metadata.processId().equals(event.correlationId())
                    || !metadata.userId().equals(event.createdBy()) || !accepted.auditInfo().updatedAt().equals(event.occurredAt())) {
                throw new IllegalStateException("Root event must match the accepted result and current request");
            }
            boolean matches = switch (event) {
                case PartyChangedOutboxCandidate changed -> accepted.partyId().equals(changed.partyId())
                        && accepted.version().equals(changed.partyVersion()) && accepted.type() == changed.partyType()
                        && accepted.recordStatus() == changed.status();
                case PartyActivatedOutboxCandidate activated -> accepted.partyId().equals(activated.partyId())
                        && accepted.version().equals(activated.partyVersion()) && accepted.type() == activated.partyType()
                        && accepted.recordStatus() == activated.status();
                default -> false;
            };
            if (!matches) {
                throw new IllegalStateException("Root event must identify the accepted aggregate version and state");
            }
            return outbox.append(session, event).invoke(() -> appended = true);
        });
    }

    void verifyOutcome(PartyMutationOutcome outcome) {
        requireActive();
        if (busy || outcome == null || !metadata.tenantId().equals(outcome.party().tenantId())) {
            throw new IllegalStateException("Mutation workflow did not finish within its tenant scope");
        }
        boolean consistent = switch (outcome.disposition()) {
            case APPLIED -> stored != null && appended && (replayScope == null || recorded)
                    && outcome.party().equals(PartyDetailsResult.fromAggregate(stored));
            case REPLAYED -> completed != null && !rootRequested && !recorded && !appended
                    && outcome.party().equals(completed.result());
        };
        if (!consistent) {
            throw new IllegalStateException("Mutation outcome does not match its completed storage work");
        }
    }

    void close() {
        active = false;
    }

    private <T> Uni<T> run(Supplier<Uni<T>> work) {
        return Uni.createFrom().deferred(() -> {
            requireActive();
            if (busy) {
                failed = true;
                throw new IllegalStateException("Mutation session operations must be sequential");
            }
            busy = true;
            return Uni.createFrom().<T>deferred(work::get).onFailure().invoke(() -> failed = true)
                    .onTermination().invoke(() -> busy = false);
        });
    }

    private void requireActive() {
        if (!active || failed || !session.isOpen() || Vertx.currentContext() != owner) {
            throw new IllegalStateException("Mutation scope is closed, failed, or used from another reactive context");
        }
    }

    private Party requireLocked() {
        return Objects.requireNonNull(locked, "A present root must be locked before using this capability");
    }

    private Party requireStored() {
        return Objects.requireNonNull(stored, "The root must be persisted before its completion or event");
    }

    private void requireReplayScope(PartyLifecycleAction action, String key) {
        if (!new ReplayScope(action, key).equals(replayScope)) {
            throw new IllegalStateException("Replay access requires the same serialized tenant/action/key scope");
        }
    }

    /** Retains the exact action/key identity within the already immutable tenant scope. */
    private record ReplayScope(PartyLifecycleAction action, String key) {
    }
}
