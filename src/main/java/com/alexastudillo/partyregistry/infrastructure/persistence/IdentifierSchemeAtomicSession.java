package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Serializes transaction rollback and session closure even when a downstream subscriber cancels. */
final class IdentifierSchemeAtomicSession {
    private IdentifierSchemeAtomicSession() { }

    static <T> Uni<T> execute(Mutiny.SessionFactory sessions, Function<Mutiny.Session, Uni<T>> work) {
        return Uni.createFrom().deferred(() -> {
            var cancellation = new CompletableFuture<T>();
            Uni<T> owned = sessions.openSession().flatMap(session -> session.withTransaction(transaction ->
                    Uni.join().first(Uni.createFrom().deferred(() -> work.apply(session)),
                            Uni.createFrom().completionStage(cancellation)).toTerminate()).eventually(session::close));
            // A per-subscription shield converts cancellation to transaction failure. Hibernate's
            // rollback then finishes before close; outer cancellation cleanup otherwise runs first.
            return owned.memoize().indefinitely().onCancellation().invoke(() ->
                    cancellation.completeExceptionally(new CancellationException("Scheme operation cancelled")));
        });
    }
}
