package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionAudit;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifier;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierVersion;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Adds test-only flushed checkpoints around the actual reactive mutation adapter without changing production acceptance. */
@Alternative
@ApplicationScoped
public class PartyIdentifierFaultPort implements PartyIdentifierMutationPort {
    private final Mutiny.SessionFactory sessions;
    private final PartyIdentifierMutationReader reader;
    private final PartyIdentifierPersistenceMapper mapper;
    private final PartyIdentifierReplayPersistence replay;
    private final PartyIdentifierAuditPersistence audit;
    private final PartyIdentifierOutboxPersistence outbox;
    private final PartyMutationTimeouts timeouts;
    private volatile Probe probe = new Probe(Phase.NONE, Mode.NONE);

    @Inject
    public PartyIdentifierFaultPort(Mutiny.SessionFactory sessions, PartyIdentifierMutationReader reader,
            PartyIdentifierPersistenceMapper mapper, PartyIdentifierReplayPersistence replay,
            PartyIdentifierAuditPersistence audit, PartyIdentifierOutboxPersistence outbox, PartyMutationTimeouts timeouts) {
        this.sessions = sessions; this.reader = reader; this.mapper = mapper; this.replay = replay;
        this.audit = audit; this.outbox = outbox; this.timeouts = timeouts;
    }

    /** Arms one isolated request checkpoint, retaining no protected request/result content. */
    public Probe arm(Phase phase, Mode mode) { probe = new Probe(phase, mode); return probe; }
    /** Restores ordinary real persistence for a fresh request or a same-key recovery attempt. */
    public void clear() { probe = new Probe(Phase.NONE, Mode.NONE); }

    @Override
    public Uni<PartyIdentifierMutationOutcome> execute(RequestMetadata metadata,
            Function<PartyIdentifierMutationContext, Uni<PartyIdentifierMutationOutcome>> work) {
        Probe active = probe;
        var factory = Mutiny.SessionFactory.class.cast(Proxy.newProxyInstance(Mutiny.SessionFactory.class.getClassLoader(),
                new Class<?>[]{Mutiny.SessionFactory.class}, (_, method, arguments) -> {
                    if (method.getName().equals("openSession") && (arguments == null || arguments.length == 0)) {
                        return sessions.openSession().invoke(active.opened::set);
                    }
                    throw new AssertionError("Unexpected mutation session factory operation");
                }));
        var adapter = new HibernateReactivePartyIdentifierMutationAdapter(factory, reader,
                new FaultWrites(mapper, reader, active), new PartyIdentifierMutationLocks(),
                new FaultEffects(replay, audit, outbox, active), timeouts);
        return adapter.execute(metadata, work).chain(outcome -> active.phase == Phase.DELIVERY
                ? Uni.createFrom().failure(new IllegalStateException("Controlled post-commit delivery failure")) : Uni.createFrom().item(outcome));
    }

    /** Names bounded transaction checkpoints rather than infer behavior from timing or logs. */
    public enum Phase { NONE, UPDATE, DEMOTION, PROMOTION, AUDIT, COMPLETION, EVENT, DELIVERY }
    /** Defines a controlled precommit failure, real SQL statement wait or uncompleted operation for timeout/cancellation. */
    public enum Mode { NONE, FAIL, SQL_WAIT, HOLD }

    /** Exposes checkpoint arrival and owned session closure without retaining domain results or reason data. */
    public static final class Probe {
        private final Phase phase;
        private final Mode mode;
        public final CompletableFuture<Void> reached = new CompletableFuture<>();
        public final AtomicReference<Mutiny.Session> opened = new AtomicReference<>();
        private Probe(Phase phase, Mode mode) { this.phase = phase; this.mode = mode; }

        private Uni<Void> checkpoint(Phase actual, Mutiny.Session session) {
            if (actual != phase) { return Uni.createFrom().voidItem(); }
            return session.flush().invoke(() -> reached.complete(null)).chain(() -> switch (mode) {
                case NONE -> Uni.createFrom().voidItem();
                case FAIL -> Uni.createFrom().failure(new IllegalStateException("Controlled flushed identifier checkpoint failure"));
                case SQL_WAIT -> session.createNativeQuery("SELECT 1 FROM pg_sleep(11)", Integer.class).getSingleResult().replaceWithVoid();
                case HOLD -> Uni.createFrom().nothing();
            });
        }
    }

    /** Adds checkpoints only after the actual guarded update and safe reload, preserving real version/constraint semantics. */
    private static final class FaultWrites extends PartyIdentifierMutationPersistence {
        private final Probe probe;
        private FaultWrites(PartyIdentifierPersistenceMapper mapper, PartyIdentifierMutationReader reader, Probe probe) {
            super(mapper, reader); this.probe = probe;
        }
        @Override Uni<PartyIdentifierResult> updateIdentifier(Mutiny.Session session, PartyIdentifier candidate, PartyIdentifierVersion expected) {
            return super.updateIdentifier(session, candidate, expected).call(() -> probe.checkpoint(Phase.UPDATE, session))
                    .call(() -> candidate.status() == PartyIdentifierStatus.VERIFIED
                            ? probe.checkpoint(candidate.isPrimary() ? Phase.PROMOTION : Phase.DEMOTION, session) : Uni.createFrom().voidItem());
        }
    }

    /** Adds checkpoints after durable audit/completion/event preparation in the same actual transaction. */
    private static final class FaultEffects extends PartyIdentifierMutationEffects {
        private final Probe probe;
        private FaultEffects(PartyIdentifierReplayPersistence replay, PartyIdentifierAuditPersistence audit,
                PartyIdentifierOutboxPersistence outbox, Probe probe) { super(replay, audit, outbox); this.probe = probe; }
        @Override Uni<Void> appendAudit(Mutiny.Session session, PartyIdentifierActionAudit history) {
            return super.appendAudit(session, history).call(() -> probe.checkpoint(Phase.AUDIT, session));
        }
        @Override Uni<Void> recordCompletion(Mutiny.Session session, PartyIdentifierIdempotencyRecordId identity, CompletedPartyIdentifierMutation completion) {
            return super.recordCompletion(session, identity, completion).call(() -> probe.checkpoint(Phase.COMPLETION, session));
        }
        @Override Uni<Void> appendEvent(Mutiny.Session session, OutboxEventCandidate event) {
            return super.appendEvent(session, event).call(() -> probe.checkpoint(Phase.EVENT, session));
        }
    }
}
