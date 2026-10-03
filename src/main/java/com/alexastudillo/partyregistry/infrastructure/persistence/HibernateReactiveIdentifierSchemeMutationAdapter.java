package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.IdentifierSchemeMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationContext;
import com.alexastudillo.partyregistry.application.port.IdentifierSchemeMutationPort;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Objects;
import java.util.function.Function;

/** Owns one fresh reactive transaction per subscription and emits only closed, committed catalog outcomes. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactiveIdentifierSchemeMutationAdapter implements IdentifierSchemeMutationPort {
    private final Mutiny.SessionFactory sessions;
    private final IdentifierSchemeMutationPersistence schemes;
    private final IdentifierSchemeReplayPersistence replay;
    private final IdentifierSchemeMutationLocks locks;
    private final PartyMutationTimeouts timeouts;

    /** Composes same-session capabilities; Application remains the owner of the complete business workflow. */
    @Inject
    public HibernateReactiveIdentifierSchemeMutationAdapter(Mutiny.SessionFactory sessions,
            IdentifierSchemeMutationPersistence schemes, IdentifierSchemeReplayPersistence replay,
            IdentifierSchemeMutationLocks locks, PartyMutationTimeouts timeouts) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.schemes = Objects.requireNonNull(schemes, "schemes");
        this.replay = Objects.requireNonNull(replay, "replay");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
    }

    @Override
    public Uni<IdentifierSchemeMutationOutcome> execute(RequestMetadata metadata,
            Function<IdentifierSchemeMutationContext, Uni<IdentifierSchemeMutationOutcome>> work) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(work, "work");
        return IdentifierSchemeAtomicSession.execute(sessions, session -> {
            var scope = new HibernateReactiveIdentifierSchemeMutationContext(session, metadata, schemes, replay, locks);
            return timeouts.limit(timeouts.configure(session)
                    .chain(() -> Uni.createFrom().deferred(() -> work.apply(scope)))
                    .invoke(scope::verifyOutcome).call(session::flush)).eventually(scope::close);
        })
                .onFailure().transform(IdentifierSchemePersistenceFailures::translate);
    }
}
