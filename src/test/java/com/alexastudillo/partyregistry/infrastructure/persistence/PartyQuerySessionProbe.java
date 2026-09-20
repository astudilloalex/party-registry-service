package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Coordinates real query transactions at the first count or batch result without production test hooks or unchecked casts.
 */
final class PartyQuerySessionProbe {

    private final Mutiny.SessionFactory delegate;
    private final boolean interceptBatches;
    private final Function<Mutiny.Session, Uni<Void>> afterFirstResult;
    private final AtomicBoolean intercepted = new AtomicBoolean();
    private final AtomicReference<Mutiny.Session> session = new AtomicReference<>();
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();
    private final AtomicLong fetchedRows = new AtomicLong();
    private final AtomicLong batches = new AtomicLong();
    private final AtomicLong maximumBatch = new AtomicLong();

    PartyQuerySessionProbe(Mutiny.SessionFactory delegate, boolean interceptBatches,
            Function<Mutiny.Session, Uni<Void>> afterFirstResult) {
        this.delegate = delegate;
        this.interceptBatches = interceptBatches;
        this.afterFirstResult = afterFirstResult;
    }

    Mutiny.SessionFactory factory() {
        Object proxy = Proxy.newProxyInstance(Mutiny.SessionFactory.class.getClassLoader(),
                new Class<?>[]{Mutiny.SessionFactory.class}, (ignored, method, arguments) -> {
                    if (method.getName().equals("withTransaction") && arguments != null && arguments.length == 1) {
                        Object work = arguments[0];
                        return delegate.withTransaction((real, transaction) -> {
                            session.set(real);
                            return invokeWork(work, wrap(real), transaction);
                        });
                    }
                    return invoke(method, delegate, arguments);
                });
        return Mutiny.SessionFactory.class.cast(proxy);
    }

    private Mutiny.Session wrap(Mutiny.Session real) {
        Object wrapped = Proxy.newProxyInstance(Mutiny.Session.class.getClassLoader(), new Class<?>[]{Mutiny.Session.class},
                (proxy, method, arguments) -> {
                    Object result = invoke(method, real, arguments);
                    if (method.getName().equals("createSelectionQuery") && arguments != null
                            && result instanceof Mutiny.SelectionQuery<?> query) {
                        return wrapQuery(query, real, arguments[1] == Object[].class);
                    }
                    return result == real ? proxy : result;
                });
        return Mutiny.Session.class.cast(wrapped);
    }

    private Object wrapQuery(Mutiny.SelectionQuery<?> query, Mutiny.Session real, boolean summaryQuery) {
        return Proxy.newProxyInstance(Mutiny.SelectionQuery.class.getClassLoader(), new Class<?>[]{Mutiny.SelectionQuery.class},
                (proxy, method, arguments) -> {
                    boolean resultMethod = method.getName().equals("getSingleResult") || method.getName().equals("getResultList");
                    if (!resultMethod) {
                        Object result = invoke(method, query, arguments);
                        return result == query ? proxy : result;
                    }
                    boolean hook = summaryQuery == interceptBatches && intercepted.compareAndSet(false, true);
                    Uni<Object> operation = hook
                            ? capture(real).chain(() -> operation(method, query, arguments))
                            : operation(method, query, arguments);
                    if (summaryQuery) {
                        operation = operation.invoke(this::recordBatch);
                    }
                    return hook ? operation.call(() -> afterFirstResult.apply(real)) : operation;
                });
    }

    private void recordBatch(Object result) {
        if (result instanceof List<?> rows && !rows.isEmpty()) {
            fetchedRows.addAndGet(rows.size());
            batches.incrementAndGet();
            maximumBatch.accumulateAndGet(rows.size(), Math::max);
        }
    }

    private Uni<Void> capture(Mutiny.Session real) {
        return real.createNativeQuery("""
                select pg_backend_pid(), current_setting('transaction_isolation'), current_setting('transaction_read_only'),
                       (select cast(setting as bigint) from pg_settings where name = 'statement_timeout')
                """, Object[].class).getSingleResult().invoke(row -> snapshot.set(new Snapshot(
                        ((Number) row[0]).intValue(), (String) row[1], (String) row[2], ((Number) row[3]).longValue())))
                .replaceWithVoid();
    }

    Snapshot snapshot() {
        return Objects.requireNonNull(snapshot.get(), "The first query must have captured its real transaction settings");
    }

    Uni<Void> awaitClosed() {
        Mutiny.Session opened = Objects.requireNonNull(session.get(), "The query must have opened a session");
        return Multi.createBy().repeating().uni(() -> Uni.createFrom().item(() -> !opened.isOpen())
                        .onItem().delayIt().by(Duration.ofMillis(5)))
                .until(Boolean.TRUE::equals).collect().last().ifNoItem().after(Duration.ofSeconds(5)).fail().replaceWithVoid();
    }

    long fetchedRows() {
        return fetchedRows.get();
    }

    long batches() {
        return batches.get();
    }

    long maximumBatch() {
        return maximumBatch.get();
    }

    private static Uni<Object> invokeWork(Object work, Mutiny.Session session, Mutiny.Transaction transaction) {
        try {
            Method apply = BiFunction.class.getMethod("apply", Object.class, Object.class);
            Object result = invoke(apply, work, new Object[]{session, transaction});
            if (result instanceof Uni<?> uni) {
                return uni.map(Function.identity());
            }
            return Uni.createFrom().failure(new IllegalStateException("Transaction work did not return a Uni"));
        } catch (Throwable failure) {
            return Uni.createFrom().failure(failure);
        }
    }

    private static Uni<Object> operation(Method method, Object target, Object[] arguments) {
        try {
            Object result = invoke(method, target, arguments);
            if (result instanceof Uni<?> uni) {
                return uni.map(Function.identity());
            }
            return Uni.createFrom().failure(new IllegalStateException("Query did not return a Uni"));
        } catch (Throwable failure) {
            return Uni.createFrom().failure(failure);
        }
    }

    private static Object invoke(Method method, Object target, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            throw Objects.requireNonNull(exception.getCause(), "invocation cause");
        }
    }

    /** Captures actual PostgreSQL snapshot settings and backend identity for consistency and cleanup assertions. */
    record Snapshot(int backendPid, String isolation, String readOnly, long statementTimeoutMillis) {
    }
}
