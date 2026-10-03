package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.CompletedIdentifierSchemeOperation;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeCreateInput;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeLifecycleRequest;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationContext;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Guards one sequential global write/replay workflow within its owning active Vert.x transaction context. */
final class HibernateReactiveIdentifierSchemeMutationContext implements IdentifierSchemeMutationContext {
    private final Mutiny.Session session;
    private final RequestMetadata metadata;
    private final IdentifierSchemeMutationPersistence schemes;
    private final IdentifierSchemeReplayPersistence replay;
    private final IdentifierSchemeMutationLocks locks;
    private final Context owner;
    private boolean active = true;
    private boolean busy;
    private boolean failed;
    private boolean targetRequested;
    private boolean replayResolved;
    private boolean codeChecked;
    private boolean codePresent;
    private boolean recorded;
    private @Nullable IdentifierSchemeIdempotencyRecordId replayScope;
    private @Nullable String code;
    private @Nullable CompletedIdentifierSchemeOperation completed;
    private @Nullable IdentifierScheme locked;
    private @Nullable IdentifierScheme stored;

    HibernateReactiveIdentifierSchemeMutationContext(Mutiny.Session session, RequestMetadata metadata,
            IdentifierSchemeMutationPersistence schemes, IdentifierSchemeReplayPersistence replay, IdentifierSchemeMutationLocks locks) {
        this.session = session;
        this.metadata = metadata;
        this.schemes = schemes;
        this.replay = replay;
        this.locks = locks;
        owner = Objects.requireNonNull(Vertx.currentContext(), "Reactive mutation context is required");
    }

    @Override
    public TenantId tenantId() {
        requireActive();
        return metadata.tenantId();
    }

    @Override
    public Uni<Void> serializeReplayKey(String operation, String key) {
        return run(() -> {
            if (targetRequested || replayScope != null) {
                throw new IllegalStateException("Serialize one replay identity before code or row locking");
            }
            var identity = new IdentifierSchemeIdempotencyRecordId(metadata.tenantId().value(), operation, key);
            return locks.serializeReplayKey(session, identity).invoke(() -> replayScope = identity);
        });
    }

    @Override
    public Uni<Optional<CompletedIdentifierSchemeOperation>> findCompleted(String operation, String key) {
        return run(() -> {
            var identity = requireReplayScope(operation, key);
            if (targetRequested || replayResolved) {
                throw new IllegalStateException("Resolve completed replay once before code or row locking");
            }
            return replay.findCompleted(session, identity).invoke(found -> {
                completed = found.orElse(null);
                replayResolved = true;
            });
        });
    }

    @Override
    public Uni<Void> serializeCode(String requested) {
        return run(() -> {
            requireTargetOrder();
            targetRequested = true;
            return locks.serializeCode(session, requested).invoke(() -> code = requested);
        });
    }

    @Override
    public Uni<Boolean> codeExists(String requested) {
        return run(() -> {
            if (!requested.equals(code) || codeChecked || stored != null) {
                throw new IllegalStateException("Check the same serialized exact code once before inserting");
            }
            return schemes.codeExists(session, requested).invoke(exists -> {
                codeChecked = true;
                codePresent = exists;
            });
        });
    }

    @Override
    public Uni<Optional<IdentifierScheme>> findForUpdate(IdentifierSchemeId id) {
        return run(() -> {
            requireTargetOrder();
            targetRequested = true;
            return schemes.findForUpdate(session, id).invoke(found -> locked = found.orElse(null));
        });
    }

    @Override
    public Uni<IdentifierScheme> insert(IdentifierScheme candidate) {
        return run(() -> {
            if (stored != null || !candidate.code().equals(code) || !codeChecked || codePresent
                    || !candidate.auditInfo().createdBy().equals(metadata.userId())) {
                throw new IllegalStateException("Insert one request-attributed draft after exact code absence");
            }
            return schemes.insert(session, candidate).invoke(result -> stored = result);
        });
    }

    @Override
    public Uni<IdentifierScheme> persistScheme(IdentifierScheme candidate, IdentifierSchemeVersion expectedVersion) {
        return run(() -> {
            var source = Objects.requireNonNull(locked, "Lock a present global scheme before writing");
            if (stored != null || !candidate.auditInfo().updatedBy().equals(metadata.userId())) {
                throw new IllegalStateException("Persist one request-attributed scheme revision");
            }
            return schemes.persistScheme(session, source, candidate, expectedVersion).invoke(result -> stored = result);
        });
    }

    @Override
    public Uni<Void> recordCompletion(String operation, String key, CompletedIdentifierSchemeOperation outcome) {
        return run(() -> {
            var identity = requireReplayScope(operation, key);
            var accepted = Objects.requireNonNull(stored, "Persist the scheme before recording completion");
            if (recorded || !operation.equals(outcome.request().operation())
                    || !IdentifierSchemeResult.fromAggregate(accepted).equals(outcome.result())) {
                throw new IllegalStateException("Record exactly the accepted scheme result in its serialized scope");
            }
            boolean matches = switch (outcome.request()) {
                case IdentifierSchemeCreateInput input -> code != null && code.equals(input.code()) && locked == null;
                case IdentifierSchemeLifecycleRequest request -> locked != null && locked.id().equals(request.schemeId())
                        && locked.version().equals(request.expectedVersion());
            };
            if (!matches) {
                throw new IllegalStateException("Completed intent does not match the scoped scheme write");
            }
            return replay.recordCompletion(session, identity, outcome).invoke(() -> recorded = true);
        });
    }

    void verifyOutcome(IdentifierSchemeMutationOutcome outcome) {
        requireActive();
        if (busy || outcome == null) {
            throw new IllegalStateException("Mutation workflow did not finish sequentially");
        }
        boolean consistent = switch (outcome.disposition()) {
            case APPLIED -> stored != null && (replayScope == null || recorded)
                    && outcome.scheme().equals(IdentifierSchemeResult.fromAggregate(stored));
            case REPLAYED -> completed != null && !targetRequested && !recorded && stored == null
                    && outcome.scheme().equals(completed.result());
        };
        if (!consistent) {
            throw new IllegalStateException("Mutation outcome does not match its completed storage work");
        }
    }

    void close() { active = false; }

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

    private void requireTargetOrder() {
        if (targetRequested || completed != null || replayScope != null && !replayResolved) {
            throw new IllegalStateException("Resolve replay before acquiring the single code or row lock");
        }
    }

    private IdentifierSchemeIdempotencyRecordId requireReplayScope(String operation, String key) {
        var identity = new IdentifierSchemeIdempotencyRecordId(metadata.tenantId().value(), operation, key);
        if (!identity.equals(replayScope)) {
            throw new IllegalStateException("Replay access requires the full serialized tenant/operation/key identity");
        }
        return identity;
    }
}
