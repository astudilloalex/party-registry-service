package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Owns bounded rejecting CPU admission and detached work cancellation with
 * results emitted on the originating Vert.x context.
 */
@Singleton
public class IdentifierSearchCpuDispatcher {
    private final ThreadPoolExecutor executor;

    @Inject
    public IdentifierSearchCpuDispatcher(
            @ConfigProperty(name = "party-registry.search.cpu-workers", defaultValue = "2") int workers,
            @ConfigProperty(name = "party-registry.search.cpu-queue-capacity", defaultValue = "32") int capacity) {
        if (workers < 1 || capacity < 1)
            throw new IllegalArgumentException("Identifier search CPU capacity must be positive");
        var sequence = new AtomicInteger();
        executor = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), runnable -> {
                    Thread thread = new Thread(runnable, "identifier-search-cpu-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Executes at most one detached batch per subscribed call; cancellation removes
     * queued work and discards late results.
     */
    public <T> Uni<T> dispatch(Function<BooleanSupplier, T> work) {
        return Uni.createFrom().deferred(() -> {
            Context origin = Objects.requireNonNull(Vertx.currentContext(), "Originating search context is required");
            Uni<T> computation = Uni.createFrom().emitter(emitter -> submit(work, emitter));
            return computation.emitOn(command -> origin.runOnContext(ignored -> command.run()));
        });
    }

    private <T> void submit(Function<BooleanSupplier, T> work, UniEmitter<? super T> emitter) {
        var cpuTask = new CpuTask<>(work, emitter);
        var future = new FutureTask<Void>(cpuTask);
        emitter.onTermination(() -> cpuTask.cancel(future, executor));
        try {
            executor.execute(future);
        } catch (RejectedExecutionException _) {
            emitter.fail(new ApplicationException(
                    new ApplicationFailure.DependencyUnavailable("identifier-search-cpu")));
        }
    }

    /** Encapsulates detached batch execution, exception translation, and unstarted queue cancellation. */
    private static final class CpuTask<T> implements Callable<Void> {
        private final Function<BooleanSupplier, T> work;
        private final UniEmitter<? super T> emitter;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private CpuTask(Function<BooleanSupplier, T> work, UniEmitter<? super T> emitter) {
            this.work = work;
            this.emitter = emitter;
        }

        @Override
        public Void call() {
            if (cancelled.get()) {
                return null;
            }
            try {
                T result = work.apply(cancelled::get);
                if (!cancelled.get()) {
                    emitter.complete(result);
                }
            } catch (RuntimeException failure) {
                if (!cancelled.get()) {
                    emitter.fail(failure);
                }
            }
            return null;
        }

        private void cancel(FutureTask<?> future, ThreadPoolExecutor executor) {
            cancelled.set(true);
            future.cancel(false);
            executor.remove(future);
        }
    }

    int queuedBatches() {
        return executor.getQueue().size();
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow().forEach(task -> {
            if (task instanceof FutureTask<?> future)
                future.cancel(false);
        });
    }
}
