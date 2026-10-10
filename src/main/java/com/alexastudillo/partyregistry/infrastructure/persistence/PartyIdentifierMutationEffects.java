package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.application.model.CompletedPartyIdentifierMutation;
import com.alexastudillo.partyregistry.application.model.OutboxEventCandidate;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierActionAudit;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.Optional;

/** Coordinates completion, private audit and optional event storage in the caller's single mutation session. */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyIdentifierMutationEffects {
    private final PartyIdentifierReplayPersistence replay;
    private final PartyIdentifierAuditPersistence audit;
    private final PartyIdentifierOutboxPersistence outbox;

    /** Composes durable side-effect stores without opening another transaction or publishing to the broker. */
    @Inject
    public PartyIdentifierMutationEffects(PartyIdentifierReplayPersistence replay, PartyIdentifierAuditPersistence audit,
            PartyIdentifierOutboxPersistence outbox) {
        this.replay = replay;
        this.audit = audit;
        this.outbox = outbox;
    }

    Uni<Optional<CompletedPartyIdentifierMutation>> findCompleted(Mutiny.Session session, PartyIdentifierIdempotencyRecordId identity) {
        return replay.findCompleted(session, identity);
    }

    Uni<Void> recordCompletion(Mutiny.Session session, PartyIdentifierIdempotencyRecordId identity, CompletedPartyIdentifierMutation completion) {
        return replay.recordCompletion(session, identity, completion);
    }

    Uni<Void> appendAudit(Mutiny.Session session, PartyIdentifierActionAudit history) { return audit.recordAudit(session, history); }

    Uni<Void> appendEvent(Mutiny.Session session, OutboxEventCandidate event) { return outbox.append(session, event); }
}
