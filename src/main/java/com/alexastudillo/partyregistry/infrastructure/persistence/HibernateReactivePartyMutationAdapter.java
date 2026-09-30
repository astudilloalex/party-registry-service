package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.PartyMutationOutcome;
import com.alexastudillo.partyregistry.application.model.ObservedOperation;
import com.alexastudillo.partyregistry.application.model.OperationOutcome;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.PartyMutationContext;
import com.alexastudillo.partyregistry.application.port.PartyMutationPort;
import com.alexastudillo.partyregistry.application.port.OperationObservationPort;
import com.alexastudillo.partyregistry.application.observability.OperationObservation;
import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Objects;
import java.util.function.Function;

/** Owns a fresh reactive transaction around Application's root workflow and emits its result only after commit. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactivePartyMutationAdapter implements PartyMutationPort {

    private final Mutiny.SessionFactory sessions;
    private final PartyRootMutationPersistence roots;
    private final PartyLifecycleIdempotencyPersistence replay;
    private final PartyRootOutboxPersistence outbox;
    private final PartyMutationTimeouts timeouts;
    private final OperationObservationPort observation;

    /** Composes only same-session storage capabilities; business precedence and eligibility remain in Application and Domain. */
    @Inject
    public HibernateReactivePartyMutationAdapter(Mutiny.SessionFactory sessions, PartyRootMutationPersistence roots,
            PartyLifecycleIdempotencyPersistence replay, PartyRootOutboxPersistence outbox, PartyMutationTimeouts timeouts,
            OperationObservationPort observation) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.roots = Objects.requireNonNull(roots, "roots");
        this.replay = Objects.requireNonNull(replay, "replay");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
        this.observation = Objects.requireNonNull(observation, "observation");
    }

    @Override
    public Uni<PartyMutationOutcome> execute(RequestMetadata metadata, Function<PartyMutationContext, Uni<PartyMutationOutcome>> work) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(work, "work");
        return OperationObservation.observe(metadata, ObservedOperation.TRANSACTION_PARTY_MUTATION, observation,
                () -> executeTransaction(metadata, work), outcome -> outcome.disposition() == PartyMutationOutcome.Disposition.REPLAYED
                        ? OperationOutcome.REPLAYED : OperationOutcome.APPLIED);
    }

    private Uni<PartyMutationOutcome> executeTransaction(RequestMetadata metadata, Function<PartyMutationContext, Uni<PartyMutationOutcome>> work) {
        return Uni.createFrom().deferred(() -> sessions.openSession().flatMap(session ->
                session.withTransaction(transaction -> {
                    var scope = new HibernateReactivePartyMutationContext(session, metadata, roots, replay, outbox);
                    return timeouts.limit(timeouts.configure(session).chain(() -> Uni.createFrom().deferred(() -> work.apply(scope)))
                            .invoke(scope::verifyOutcome).call(session::flush)).eventually(scope::close);
                }).eventually(session::close)))
                .onFailure(failure -> !(failure instanceof DomainValidationException)
                        && PersistenceExceptionTranslator.requiresTranslation(failure))
                .transform(PersistenceExceptionTranslator::toApplicationException);
    }
}
