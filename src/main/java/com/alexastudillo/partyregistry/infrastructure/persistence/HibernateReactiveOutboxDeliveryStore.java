package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.infrastructure.messaging.ClaimedOutboxEvent;
import com.alexastudillo.partyregistry.infrastructure.messaging.OutboxDeliveryStore;
import com.alexastudillo.partyregistry.infrastructure.messaging.OutboxMessage;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.LockMode;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Implements short PostgreSQL lease and delivery transitions with Hibernate Reactive.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class HibernateReactiveOutboxDeliveryStore implements OutboxDeliveryStore {

    static final String CLAIM_QUERY = """
            select *
            from party_outbox_events
            where status = 'PENDING'
              and (next_attempt_at is null or next_attempt_at <= :claimedAt)
            order by next_attempt_at asc nulls first, created_at asc, id asc
            limit :batchSize
            for update skip locked
            """;

    private static final String TRANSITION_QUERY = """
            from PartyOutboxEventEntity event
            where event.id = :eventId
              and event.status = :status
              and event.publishAttempts = :claimedAttempt
            """;

    private static final String PUBLISHER_ID = "outbox-publisher";

    private final Mutiny.SessionFactory sessionFactory;

    /**
     * Creates the store from the reactive session factory.
     */
    @Inject
    public HibernateReactiveOutboxDeliveryStore(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
    }

    @Override
    public Uni<List<ClaimedOutboxEvent>> claimDue(
            int batchSize,
            Instant claimedAt,
            Duration lease) {
        return sessionFactory.withTransaction((session, transaction) -> session
                .createNativeQuery(CLAIM_QUERY, PartyOutboxEventEntity.class)
                .setParameter("claimedAt", claimedAt)
                .setParameter("batchSize", batchSize)
                .getResultList()
                .invoke(events -> events.forEach(event -> event.claimForPublication(
                        claimedAt,
                        lease,
                        PUBLISHER_ID)))
                .call(session::flush)
                .map(events -> events.stream()
                        .map(HibernateReactiveOutboxDeliveryStore::toClaimedEvent)
                        .toList()));
    }

    @Override
    public Uni<Void> markPublished(
            UUID eventId,
            int claimedAttempt,
            Instant acknowledgedAt) {
        return transition(
                eventId,
                claimedAttempt,
                event -> event.markPublished(claimedAttempt, acknowledgedAt, PUBLISHER_ID));
    }

    @Override
    public Uni<Void> scheduleRetry(
            UUID eventId,
            int claimedAttempt,
            Instant failedAt,
            Instant retryAt,
            String errorCode) {
        return transition(
                eventId,
                claimedAttempt,
                event -> event.scheduleRetry(
                        claimedAttempt,
                        failedAt,
                        retryAt,
                        errorCode,
                        PUBLISHER_ID));
    }

    @Override
    public Uni<Void> markFailed(
            UUID eventId,
            int claimedAttempt,
            Instant failedAt,
            String errorCode) {
        return transition(
                eventId,
                claimedAttempt,
                event -> event.markFailed(
                        claimedAttempt,
                        failedAt,
                        errorCode,
                        PUBLISHER_ID));
    }

    private Uni<Void> transition(
            UUID eventId,
            int claimedAttempt,
            Consumer<PartyOutboxEventEntity> transition) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(transition, "transition");
        return sessionFactory.withTransaction((session, transaction) -> session
                .createQuery(TRANSITION_QUERY, PartyOutboxEventEntity.class)
                .setParameter("eventId", eventId)
                .setParameter("status", PartyOutboxStatus.PENDING)
                .setParameter("claimedAttempt", claimedAttempt)
                .setLockMode(LockMode.PESSIMISTIC_WRITE)
                .getSingleResultOrNull()
                .invoke(event -> {
                    if (event != null) {
                        transition.accept(event);
                    }
                })
                .call(session::flush)
                .replaceWithVoid());
    }

    private static ClaimedOutboxEvent toClaimedEvent(PartyOutboxEventEntity entity) {
        OutboxMessage message = new OutboxMessage(
                entity.id(),
                entity.tenantId(),
                entity.aggregateType().name(),
                entity.aggregateId(),
                entity.aggregateVersion(),
                entity.eventType(),
                entity.eventSchemaVersion(),
                entity.payload(),
                entity.occurredAt(),
                entity.correlationId(),
                entity.causationId());
        return new ClaimedOutboxEvent(message, entity.publishAttempts());
    }
}
