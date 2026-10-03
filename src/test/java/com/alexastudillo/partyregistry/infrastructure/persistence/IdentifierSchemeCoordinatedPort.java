package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.*;
import com.alexastudillo.partyregistry.application.port.*;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Coordinates real persistence acceptance and injects failures without replacing business workflows or database transactions. */
@Alternative
@ApplicationScoped
public class IdentifierSchemeCoordinatedPort implements IdentifierSchemeMutationPort, IdentifierSchemeReadPort {
    @Inject Mutiny.SessionFactory sessions;
    @Inject IdentifierSchemeMutationPersistence schemes;
    @Inject IdentifierSchemeReplayPersistence replay;
    @Inject IdentifierSchemeMutationLocks locks;
    @Inject IdentifierSchemePersistenceMapper mapper;
    @Inject PartyMutationTimeouts mutationTimeouts;
    @Inject PartyQueryTimeouts queryTimeouts;
    private final Map<UUID, Probe> probes = new ConcurrentHashMap<>();

    /** Installs a process-specific checkpoint; independent contenders always use unmodified real adapters. */
    public Probe arm(UUID process, Mode mode) {
        var probe = new Probe(mode);
        probes.put(process, probe);
        return probe;
    }

    public void clear() {
        probes.values().forEach(probe -> probe.release.complete(null));
        probes.clear();
    }

    @Override
    public Uni<IdentifierSchemeMutationOutcome> execute(RequestMetadata metadata,
            Function<IdentifierSchemeMutationContext, Uni<IdentifierSchemeMutationOutcome>> work) {
        var probe = probes.get(metadata.processId());
        var factory = probe == null ? sessions : factory(probe, false);
        var budgets = probe == null ? mutationTimeouts : switch (probe.mode) {
            case OPERATION_TIMEOUT -> new PartyMutationTimeouts(new MutationBudgets(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofMillis(250)));
            case LOCK_TIMEOUT -> new PartyMutationTimeouts(new MutationBudgets(Duration.ofMillis(300), Duration.ofSeconds(1), Duration.ofSeconds(2)));
            default -> mutationTimeouts;
        };
        var adapter = new HibernateReactiveIdentifierSchemeMutationAdapter(factory, schemes, replay, locks, budgets);
        var committed = adapter.execute(metadata, context -> {
            var effective = probe != null && probe.mode == Mode.FAIL_RECORD ? failRecording(context, probe) : context;
            return work.apply(effective).call(() -> {
                if (probe == null) return Uni.createFrom().voidItem();
                return probe.session.get().flush().chain(() -> probe.session.get().createNativeQuery("select pg_backend_pid()", Integer.class)
                        .getSingleResult().invoke(pid -> probe.pid = pid)).chain(() -> {
                    if (probe.mode != Mode.LOSE_AFTER_COMMIT) probe.reached.complete(null);
                    return switch (probe.mode) {
                        case HOLD -> Uni.createFrom().completionStage(probe.release);
                        case FAIL_ACCEPTANCE -> Uni.createFrom().failure(new IllegalStateException("Controlled failure before acceptance"));
                        case OPERATION_TIMEOUT -> Uni.createFrom().nothing();
                        default -> Uni.createFrom().voidItem();
                    };
                });
            });
        });
        if (probe != null && probe.mode == Mode.LOSE_AFTER_COMMIT) {
            return committed.invoke(outcome -> probe.reached.complete(null)).chain(() -> Uni.createFrom().nothing());
        }
        return committed;
    }

    private IdentifierSchemeMutationContext failRecording(IdentifierSchemeMutationContext context, Probe probe) {
        return IdentifierSchemeMutationContext.class.cast(Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IdentifierSchemeMutationContext.class}, (ignored, method, arguments) -> {
                    Object result;
                    try { result = method.invoke(context, arguments); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                    if (method.getName().equals("recordCompletion") && result instanceof Uni<?> operation) {
                        return operation.call(() -> probe.session.get().flush()).chain(() ->
                                Uni.createFrom().failure(new IllegalStateException("Controlled completion storage failure")));
                    }
                    return result;
                }));
    }

    /** Waits for the exact advisory lock, proving the HTTP contender has entered PostgreSQL before release. */
    public Uni<Void> awaitKeyWait(String tenant, String operation, String key) {
        return awaitAdvisory(IdentifierSchemeMutationLocks.replayLockId(
                new IdentifierSchemeIdempotencyRecordId(UUID.fromString(tenant), operation, key)));
    }

    public Uni<Void> awaitCodeWait(String code) {
        return awaitAdvisory(IdentifierSchemeMutationLocks.codeLockId(code));
    }

    private Uni<Void> awaitAdvisory(long id) {
        return poll("""
                select exists (select 1 from pg_locks where locktype = 'advisory' and not granted
                  and classid::bigint = :upper and objid::bigint = :lower and objsubid = 1)
                """, query -> query.setParameter("upper", id >>> 32).setParameter("lower", id & 0xffffffffL));
    }

    /** Matches the contender's transaction lock to the held scheme writer's backend, excluding unrelated waits. */
    public Uni<Void> awaitRowWait(Probe holder) {
        return poll("""
                        select exists (select 1 from pg_locks waiting join pg_locks holding
                          on waiting.transactionid = holding.transactionid
                          where waiting.locktype = 'transactionid' and not waiting.granted
                            and holding.granted and holding.pid = :pid)
                        """, query -> query.setParameter("pid", holder.pid));
    }

    private Uni<Void> poll(String sql, Function<Mutiny.SelectionQuery<Boolean>, Mutiny.SelectionQuery<Boolean>> parameters) {
        return sessions.openSession().flatMap(session -> Multi.createBy().repeating()
                .uni(() -> parameters.apply(session.createNativeQuery(sql, Boolean.class)).getSingleResult())
                .until(Boolean.TRUE::equals).collect().last().ifNoItem().after(Duration.ofSeconds(4)).fail()
                .eventually(session::close)).replaceWithVoid();
    }

    private Mutiny.SessionFactory factory(Probe probe, boolean slowRead) {
        return Mutiny.SessionFactory.class.cast(Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Mutiny.SessionFactory.class}, (ignored, method, arguments) -> {
                    if (!method.getName().equals("openSession")) throw new AssertionError(method.getName());
                    return sessions.openSession().invoke(probe.session::set).map(session -> {
                        if (!slowRead) return session;
                        return Mutiny.Session.class.cast(Proxy.newProxyInstance(getClass().getClassLoader(),
                                new Class<?>[]{Mutiny.Session.class}, (proxy, operation, values) -> {
                                    if (operation.getName().equals("createSelectionQuery")) {
                                        return session.createNativeQuery("""
                                                select scheme.* from identifier_schemes scheme cross join pg_sleep(1)
                                                where scheme.id = :id
                                                """, IdentifierSchemeEntity.class);
                                    }
                                    try { return operation.invoke(session, values); }
                                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                                }));
                    });
                }));
    }

    /** Selects a real slow query for one target; normal reads still use the production read adapter. */
    public void slowRead(UUID id, Probe probe) { probes.put(id, probe); }

    @Override
    public Uni<Optional<IdentifierSchemeResult>> findById(IdentifierSchemeId id) {
        var probe = probes.get(id.value());
        if (probe == null) return reader().findById(id);
        var limits = probe.mode == Mode.QUERY_STATEMENT_TIMEOUT
                ? new QueryBudgets(Duration.ofMillis(150), Duration.ofSeconds(2))
                : new QueryBudgets(Duration.ofSeconds(2), Duration.ofMillis(150));
        return new HibernateReactiveIdentifierSchemeReadAdapter(factory(probe, true), mapper, new PartyQueryTimeouts(limits)).findById(id);
    }

    @Override
    public Uni<Optional<IdentifierSchemeResult>> findByCode(String code) { return reader().findByCode(code); }

    @Override
    public Uni<IdentifierSchemePageSlice> findPage(IdentifierSchemeSearchCriteria criteria, Optional<IdentifierSchemePageBoundary> boundary) {
        return reader().findPage(criteria, boundary);
    }

    private HibernateReactiveIdentifierSchemeReadAdapter reader() {
        return new HibernateReactiveIdentifierSchemeReadAdapter(sessions, mapper, queryTimeouts);
    }

    /** Represents an acceptance barrier and its owned real Hibernate session, with finite test-side waits. */
    public static final class Probe {
        private final Mode mode;
        private volatile int pid;
        private final AtomicReference<Mutiny.Session> session = new AtomicReference<>();
        private final CompletableFuture<Void> reached = new CompletableFuture<>();
        private final CompletableFuture<Void> release = new CompletableFuture<>();
        private Probe(Mode mode) { this.mode = mode; }
        public Uni<Void> reached() { return Uni.createFrom().completionStage(reached).ifNoItem().after(Duration.ofSeconds(4)).fail(); }
        public void release() { release.complete(null); }
        public boolean closed() { return session.get() != null && !session.get().isOpen(); }
    }

    /** Limits fault injection to selected storage checkpoints and real production deadlines. */
    public enum Mode { HOLD, FAIL_RECORD, FAIL_ACCEPTANCE, LOCK_TIMEOUT, OPERATION_TIMEOUT, QUERY_STATEMENT_TIMEOUT, QUERY_TRAVERSAL_TIMEOUT, LOSE_AFTER_COMMIT }

    /** Supplies short test mutation limits through the existing production configuration contract. */
    private record MutationBudgets(Duration lockTimeout, Duration statementTimeout, Duration operationTimeout) implements PartyMutationConfiguration { }
    /** Supplies short test query limits through the existing production configuration contract. */
    private record QueryBudgets(Duration statementTimeout, Duration traversalTimeout) implements PartyQueryConfiguration { }
}
