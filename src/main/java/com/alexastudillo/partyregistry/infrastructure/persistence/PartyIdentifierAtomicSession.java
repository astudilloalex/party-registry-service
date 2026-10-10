package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Owns identifier rollback before session closure when the downstream request cancels. */
final class PartyIdentifierAtomicSession {
    private PartyIdentifierAtomicSession() { }

    static <T> Uni<T> execute(Mutiny.SessionFactory sessions, Function<Mutiny.Session, Uni<T>> work) {
        return Uni.createFrom().deferred(() -> {
            var cancellation = new CompletableFuture<T>();
            Uni<T> owned = sessions.openSession().flatMap(session -> session.withTransaction(_ ->
                    Uni.join().first(Uni.createFrom().deferred(() -> work.apply(session)),
                            Uni.createFrom().completionStage(cancellation)).toTerminate()).eventually(session::close));
            // Keep transaction cleanup subscribed until rollback finishes instead of racing close against cancellation.
            return owned.memoize().indefinitely().onCancellation().invoke(() ->
                    cancellation.completeExceptionally(new CancellationException("Identifier operation cancelled")));
        });
    }
}
