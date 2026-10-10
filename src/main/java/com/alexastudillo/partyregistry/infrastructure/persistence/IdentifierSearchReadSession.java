package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.hibernate.reactive.mutiny.Mutiny;

/** Owns finite search acquisition, repeatable-read work and cancellation-safe transaction/session cleanup. */
@Singleton
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class IdentifierSearchReadSession {
    private final Mutiny.SessionFactory sessions;
    private final IdentifierSchemePersistenceMapper schemes;
    private final Duration budget;
    private final long budgetNanos;
    private final long statementMillis;

    @Inject
    public IdentifierSearchReadSession(Mutiny.SessionFactory sessions, IdentifierSchemePersistenceMapper schemes,
            PartyQueryConfiguration configuration) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.schemes = Objects.requireNonNull(schemes, "schemes");
        budget = configuration.traversalTimeout();
        statementMillis = configuration.statementTimeout().toMillis();
        if (budget.isNegative() || budget.isZero() || statementMillis < 1 || statementMillis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Identifier search deadlines are invalid");
        }
        budgetNanos = budget.toNanos();
    }

    <T> Uni<T> read(Function<ReadView, Uni<T>> work) {
        return Uni.createFrom().deferred(() -> {
            Context origin = Objects.requireNonNull(Vertx.currentContext(), "Originating search context is required");
            long started = System.nanoTime();
            var stopped = new AtomicBoolean();
            var cancellation = new CompletableFuture<T>();
            // Keep late acquisition owned: a timed-out/cancelled waiter must close any subsequently allocated session.
            Uni<Mutiny.Session> acquisition = sessions.openSession().flatMap(session -> stopped.get()
                    ? session.close().chain(() -> Uni.createFrom().<Mutiny.Session>failure(cancelled()))
                    : Uni.createFrom().item(session)).memoize().indefinitely();
            return acquisition.ifNoItem().after(budget).fail()
                    .onFailure().invoke(() -> stopped.set(true))
                    .emitOn(command -> origin.runOnContext(ignored -> command.run()))
                    .flatMap(session -> {
                        var view = new ReadView(session, origin);
                        Duration remaining = Duration.ofNanos(budgetNanos - (System.nanoTime() - started));
                        Uni<T> owned = session.withTransaction(transaction -> {
                            session.setDefaultReadOnly(true);
                            Uni<T> operation = remaining.isNegative() || remaining.isZero()
                                    ? Uni.createFrom().failure(new io.smallrye.mutiny.TimeoutException())
                                    : configure(session, remaining).chain(() -> work.apply(view))
                                            .ifNoItem().after(remaining).fail()
                                            .emitOn(command -> origin.runOnContext(ignored -> command.run()));
                            return Uni.join().first(operation, Uni.createFrom().completionStage(cancellation)
                                    .emitOn(command -> origin.runOnContext(ignored -> command.run()))).toTerminate();
                        }).eventually(() -> {
                            view.close();
                            return session.close();
                        });
                        return owned.memoize().indefinitely();
                    })
                    .onCancellation().invoke(() -> {
                        stopped.set(true);
                        cancellation.completeExceptionally(cancelled());
                    })
                    .onFailure().transform(PartyIdentifierPersistenceFailures::translate);
        });
    }

    private Uni<Void> configure(Mutiny.Session session, Duration remaining) {
        long millis = Math.clamp(remaining.toMillis(), 1, statementMillis);
        return session.createNativeQuery("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY").executeUpdate()
                .chain(() -> session.createNativeQuery("select set_config('statement_timeout', :timeout, true)", String.class)
                        .setParameter("timeout", Long.toString(millis)).getSingleResult()).replaceWithVoid();
    }

    private static CancellationException cancelled() {
        return new CancellationException("Identifier search cancelled");
    }

    /** Guards sequential same-context operations and exact scheme resolution until the owning read completes. */
    final class ReadView {
        private final Mutiny.Session session;
        private final Context origin;
        private final AtomicBoolean busy = new AtomicBoolean();
        private boolean closed;

        private ReadView(Mutiny.Session session, Context origin) {
            this.session = session;
            this.origin = origin;
        }

        Uni<Optional<IdentifierScheme>> findSchemeByCode(String code) {
            if (!com.alexastudillo.partyregistry.infrastructure.security.JcaIdentifierProtectionAdapter.isLosslessSelector(code)) {
                return query(ignored -> Uni.createFrom().item(Optional.empty()));
            }
            return query(current -> current.createSelectionQuery("from IdentifierSchemeEntity s where s.code = :code", IdentifierSchemeEntity.class)
                    .setParameter("code", code).getSingleResultOrNull().map(entity -> Optional.ofNullable(entity).map(schemes::toDomain)));
        }

        <T> Uni<T> query(Function<Mutiny.Session, Uni<T>> work) {
            return Uni.createFrom().deferred(() -> {
                if (closed || Vertx.currentContext() != origin || !busy.compareAndSet(false, true)) {
                    return Uni.createFrom().failure(new IllegalStateException("Identifier search read context is unavailable"));
                }
                return Uni.createFrom().deferred(() -> work.apply(session)).onTermination().invoke(() -> busy.set(false));
            });
        }

        private void close() {
            closed = true;
        }
    }
}
