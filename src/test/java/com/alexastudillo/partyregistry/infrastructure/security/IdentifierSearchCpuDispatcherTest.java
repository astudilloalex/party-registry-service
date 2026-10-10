package com.alexastudillo.partyregistry.infrastructure.security;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import com.alexastudillo.partyregistry.support.PersistenceTestProfile;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves detached worker execution, same-origin emission, rejecting saturation
 * and queued cancellation without caller fallback.
 */
@QuarkusTest
@TestProfile(PersistenceTestProfile.class)
class IdentifierSearchCpuDispatcherTest {
    @Test
    void rejectsUnboundedOrDisabledCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new IdentifierSearchCpuDispatcher(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new IdentifierSearchCpuDispatcher(1, 0));
    }

    @Test
    @RunOnVertxContext
    void executesOffLoopAndEmitsOnIdenticalContext(UniAsserter asserter) {
        var dispatcher = new IdentifierSearchCpuDispatcher(1, 1);
        Context origin = Vertx.currentContext();
        asserter.assertEquals(() -> dispatcher.dispatch(cancelled -> {
            assertNull(Vertx.currentContext());
            assertTrue(Thread.currentThread().getName().startsWith("identifier-search-cpu-"));
            return "safe";
        }).invoke(item -> assertSame(origin, Vertx.currentContext())), "safe");
        asserter.execute(dispatcher::close);
    }

    @Test
    @RunOnVertxContext
    void rejectsSaturationAndRemovesCancelledQueuedWork(UniAsserter asserter) {
        var dispatcher = new IdentifierSearchCpuDispatcher(1, 1);
        Context origin = Vertx.currentContext();
        var entered = new CompletableFuture<Void>();
        var release = new CountDownLatch(1);
        var ranQueued = new AtomicBoolean();
        var busy = new AtomicReference<UniAssertSubscriber<String>>();
        var queued = new AtomicReference<UniAssertSubscriber<String>>();
        asserter.execute(() -> busy.set(dispatcher.dispatch(cancelled -> {
            entered.complete(null);
            try {
                if (!release.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("Test CPU barrier timed out");
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test CPU barrier interrupted");
            }
            return "released";
        }).subscribe().withSubscriber(UniAssertSubscriber.create())));
        asserter.execute(() -> Uni.createFrom().completionStage(entered)
                .emitOn(command -> origin.runOnContext(ignored -> command.run())));
        asserter.execute(() -> queued.set(dispatcher.dispatch(cancelled -> {
            ranQueued.set(true);
            return "queued";
        }).subscribe().withSubscriber(UniAssertSubscriber.create())));
        asserter.assertFailedWith(() -> dispatcher.dispatch(cancelled -> "rejected"), failure -> {
            var unavailable = assertInstanceOf(ApplicationException.class, failure);
            assertInstanceOf(ApplicationFailure.DependencyUnavailable.class, unavailable.failure());
        });
        asserter.execute(() -> {
            queued.get().cancel();
            assertEquals(0, dispatcher.queuedBatches());
            busy.get().cancel();
            release.countDown();
        });
        asserter.assertEquals(() -> dispatcher.dispatch(cancelled -> "healthy"), "healthy");
        asserter.execute(() -> {
            assertFalse(ranQueued.get());
            dispatcher.close();
        });
    }
}
