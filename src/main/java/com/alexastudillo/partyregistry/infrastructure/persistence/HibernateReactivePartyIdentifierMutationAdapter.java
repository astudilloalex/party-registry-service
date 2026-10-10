package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierMutationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyIdentifierMutationPort;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Objects;
import java.util.function.Function;

/** Owns a fresh reactive identifier transaction and emits only committed, closed-scope outcomes. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactivePartyIdentifierMutationAdapter implements PartyIdentifierMutationPort {
    private final Mutiny.SessionFactory sessions;
    private final PartyIdentifierMutationReader reader;
    private final PartyIdentifierMutationPersistence writes;
    private final PartyIdentifierMutationLocks locks;
    private final PartyIdentifierMutationEffects effects;
    private final PartyMutationTimeouts timeouts;
    private final OperationObservationPort observation;

    HibernateReactivePartyIdentifierMutationAdapter(Mutiny.SessionFactory sessions, PartyIdentifierMutationReader reader,
            PartyIdentifierMutationPersistence writes, PartyIdentifierMutationLocks locks, PartyIdentifierMutationEffects effects,
            PartyMutationTimeouts timeouts) {
        this(sessions, reader, writes, locks, effects, timeouts, OperationObservation.noop());
    }

    /** Composes same-session persistence mechanics around the Application-owned callback. */
    @Inject
    public HibernateReactivePartyIdentifierMutationAdapter(Mutiny.SessionFactory sessions, PartyIdentifierMutationReader reader,
            PartyIdentifierMutationPersistence writes, PartyIdentifierMutationLocks locks, PartyIdentifierMutationEffects effects,
            PartyMutationTimeouts timeouts, OperationObservationPort observation) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.writes = Objects.requireNonNull(writes, "writes");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
        this.observation = Objects.requireNonNull(observation, "observation");
    }

    @Override
    public Uni<PartyIdentifierMutationOutcome> execute(RequestMetadata metadata,
            Function<PartyIdentifierMutationContext, Uni<PartyIdentifierMutationOutcome>> work) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(work, "work");
        return OperationObservation.observe(metadata, ObservedOperation.TRANSACTION_PARTY_IDENTIFIER_MUTATION, observation,
                () -> executeTransaction(metadata, work), outcome -> outcome.disposition() == PartyIdentifierMutationOutcome.Disposition.REPLAYED
                        ? OperationOutcome.REPLAYED : OperationOutcome.APPLIED);
    }

    private Uni<PartyIdentifierMutationOutcome> executeTransaction(RequestMetadata metadata,
            Function<PartyIdentifierMutationContext, Uni<PartyIdentifierMutationOutcome>> work) {
        return PartyIdentifierAtomicSession.execute(sessions, session -> {
            var context = new HibernateReactivePartyIdentifierMutationContext(session, metadata, reader, writes, locks, effects);
            return timeouts.limit(timeouts.configure(session).chain(() -> Uni.createFrom().deferred(() -> work.apply(context)))
                    .invoke(context::verifyOutcome).call(session::flush)).eventually(context::close);
        }).onFailure().transform(PartyIdentifierPersistenceFailures::translate);
    }
}
