package com.alexastudillo.partyregistry.infrastructure.messaging;

import com.alexastudillo.partyregistry.infrastructure.configuration.PartyOutboxPersistenceMode;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/**
 * Claims and publishes due Party outbox events without spanning broker calls
 * with transactions.
 */
@ApplicationScoped
@IfBuildProperty(name = "quarkus.hibernate-orm.enabled", stringValue = "true", enableIfMissing = true)
public class PartyOutboxPublisher {

    static final int MAX_BATCH_SIZE = 500;
    static final int MAX_ATTEMPTS = 100;
    static final Duration MAX_POLL_INTERVAL = Duration.ofHours(1);
    static final Duration MAX_PUBLISH_TIMEOUT = Duration.ofMinutes(5);
    static final Duration MAX_CLAIM_LEASE = Duration.ofHours(1);
    static final Duration MAX_BACKOFF = Duration.ofHours(24);

    private static final Logger LOG = Logger.getLogger(PartyOutboxPublisher.class);
    private static final String BROKER_TIMEOUT = "broker-timeout";
    private static final String BROKER_UNAVAILABLE = "broker-unavailable";
    private static final String SERIALIZATION_FAILED = "serialization-failed";

    private final OutboxDeliveryStore store;
    private final OutboxMessageSender sender;
    private final OutboxMessageSerializer serializer;
    private final OutboxPublisherConfiguration configuration;
    private final PartyOutboxPersistenceMode mode;
    private final Clock clock;

    /**
     * Creates the scheduled publisher with validated bounded settings.
     */
    @Inject
    public PartyOutboxPublisher(
            OutboxDeliveryStore store,
            OutboxMessageSender sender,
            OutboxMessageSerializer serializer,
            OutboxPublisherConfiguration configuration,
            @ConfigProperty(name = "party-registry.outbox.mode") String mode) {
        this(store, sender, serializer, configuration, mode, Clock.systemUTC());
    }

    PartyOutboxPublisher(
            OutboxDeliveryStore store,
            OutboxMessageSender sender,
            OutboxMessageSerializer serializer,
            OutboxPublisherConfiguration configuration,
            String mode,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.mode = PartyOutboxPersistenceMode.fromConfiguration(mode);
        this.clock = Objects.requireNonNull(clock, "clock");
        validateConfiguration(configuration);
    }

    /**
     * Publishes one bounded batch while preventing overlapping runs in this
     * application instance.
     *
     * @return a pipeline that completes after every ordinary event outcome is
     *         recorded
     */
    @Scheduled(identity = "party-outbox-publisher", every = "${party-registry.outbox.publisher.poll-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public Uni<Void> publishDueEvents() {
        if (!mode.publishesEvents()) {
            return Uni.createFrom().voidItem();
        }
        Instant claimedAt = clock.instant();
        return store.claimDue(
                configuration.batchSize(),
                claimedAt,
                configuration.claimLease())
                .flatMap(this::publishClaimedEvents);
    }

    private Uni<Void> publishClaimedEvents(List<ClaimedOutboxEvent> events) {
        if (events.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        if (events.size() == 1) {
            return publishClaimedEvent(events.getFirst());
        }
        List<Uni<Void>> publications = events.stream()
                .map(this::publishClaimedEvent)
                .toList();
        return Uni.combine().all().unis(publications).discardItems();
    }

    private Uni<Void> publishClaimedEvent(ClaimedOutboxEvent claimedEvent) {
        return Uni.createFrom().deferred(() -> {
            String serializedMessage;
            try {
                serializedMessage = serializer.serialize(claimedEvent.message());
            } catch (CancellationException cancellation) {
                return Uni.createFrom().failure(cancellation);
            } catch (RuntimeException _) {
                return recordTerminalFailure(claimedEvent, SERIALIZATION_FAILED);
            }
            return sendAndAcknowledge(claimedEvent, serializedMessage);
        });
    }

    private Uni<Void> sendAndAcknowledge(
            ClaimedOutboxEvent claimedEvent,
            String serializedMessage) {
        return Uni.createFrom().deferred(() -> sender.send(
                claimedEvent.message(),
                serializedMessage))
                .ifNoItem().after(configuration.publishTimeout())
                .failWith(TimeoutException::new)
                .flatMap(ignored -> markPublished(claimedEvent))
                .onFailure(failure -> !isCancellation(failure))
                .recoverWithUni(failure -> recordDeliveryFailure(claimedEvent, failure));
    }

    private Uni<Void> markPublished(ClaimedOutboxEvent claimedEvent) {
        return store.markPublished(
                claimedEvent.message().eventId(),
                claimedEvent.publishAttempt(),
                clock.instant())
                .onFailure(failure -> !isCancellation(failure))
                .recoverWithUni(failure -> ignoredTransitionFailure(
                        claimedEvent,
                        "publish-transition-failed"));
    }

    private Uni<Void> recordDeliveryFailure(
            ClaimedOutboxEvent claimedEvent,
            Throwable failure) {
        String errorCode = hasCause(failure, TimeoutException.class)
                ? BROKER_TIMEOUT
                : BROKER_UNAVAILABLE;
        if (claimedEvent.publishAttempt() >= configuration.maximumAttempts()) {
            return recordTerminalFailure(claimedEvent, errorCode);
        }
        Instant failedAt = clock.instant();
        Instant retryAt = failedAt.plus(backoffFor(claimedEvent.publishAttempt()));
        return store.scheduleRetry(
                claimedEvent.message().eventId(),
                claimedEvent.publishAttempt(),
                failedAt,
                retryAt,
                errorCode)
                .onFailure(transitionFailure -> !isCancellation(transitionFailure))
                .recoverWithUni(transitionFailure -> ignoredTransitionFailure(
                        claimedEvent,
                        "retry-transition-failed"));
    }

    private Uni<Void> recordTerminalFailure(
            ClaimedOutboxEvent claimedEvent,
            String errorCode) {
        return store.markFailed(
                claimedEvent.message().eventId(),
                claimedEvent.publishAttempt(),
                clock.instant(),
                errorCode)
                .onFailure(failure -> !isCancellation(failure))
                .recoverWithUni(failure -> ignoredTransitionFailure(
                        claimedEvent,
                        "terminal-transition-failed"));
    }

    private Uni<Void> ignoredTransitionFailure(
            ClaimedOutboxEvent claimedEvent,
            String outcome) {
        LOG.warnf(
                "Outbox delivery state was not updated [eventId=%s, attempt=%d, outcome=%s]",
                claimedEvent.message().eventId(),
                claimedEvent.publishAttempt(),
                outcome);
        return Uni.createFrom().voidItem();
    }

    private Duration backoffFor(int attempt) {
        Duration delay = configuration.initialBackoff();
        Duration maximum = configuration.maximumBackoff();
        for (int currentAttempt = 1; currentAttempt < attempt && delay.compareTo(maximum) < 0; currentAttempt++) {
            if (delay.compareTo(maximum.dividedBy(2)) >= 0) {
                return maximum;
            }
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(maximum) > 0 ? maximum : delay;
    }

    private static boolean isCancellation(Throwable failure) {
        return hasCause(failure, CancellationException.class);
    }

    private static boolean hasCause(
            Throwable failure,
            Class<? extends Throwable> expectedType) {
        Throwable current = failure;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void validateConfiguration(OutboxPublisherConfiguration configuration) {
        requireRange("poll interval", configuration.pollInterval(), MAX_POLL_INTERVAL);
        if (configuration.batchSize() <= 0 || configuration.batchSize() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Outbox batch size is outside its supported range");
        }
        requireRange("publish timeout", configuration.publishTimeout(), MAX_PUBLISH_TIMEOUT);
        requireRange("claim lease", configuration.claimLease(), MAX_CLAIM_LEASE);
        if (configuration.claimLease().compareTo(configuration.publishTimeout()) <= 0) {
            throw new IllegalArgumentException("Outbox claim lease must exceed the publish timeout");
        }
        requireRange("initial backoff", configuration.initialBackoff(), MAX_BACKOFF);
        requireRange("maximum backoff", configuration.maximumBackoff(), MAX_BACKOFF);
        if (configuration.maximumBackoff().compareTo(configuration.initialBackoff()) < 0) {
            throw new IllegalArgumentException("Outbox maximum backoff cannot be below its initial backoff");
        }
        if (configuration.maximumAttempts() <= 0
                || configuration.maximumAttempts() > MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Outbox maximum attempts is outside its supported range");
        }
    }

    private static void requireRange(String name, Duration value, Duration maximum) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Outbox " + name + " is outside its supported range");
        }
    }
}
