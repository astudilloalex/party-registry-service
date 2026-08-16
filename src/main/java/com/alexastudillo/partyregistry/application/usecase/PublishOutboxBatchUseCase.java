package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.OutboxBatchResult;
import com.alexastudillo.partyregistry.application.OutboxClaim;
import com.alexastudillo.partyregistry.application.PublicationOutcome;
import com.alexastudillo.partyregistry.application.RecordedOutboxOutcome;
import com.alexastudillo.partyregistry.application.port.IntegrationEventPublisherPort;
import com.alexastudillo.partyregistry.application.port.OutboxStorePort;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class PublishOutboxBatchUseCase {
    private final OutboxStorePort store;
    private final IntegrationEventPublisherPort publisher;

    public PublishOutboxBatchUseCase(OutboxStorePort store, IntegrationEventPublisherPort publisher) {
        this.store = Objects.requireNonNull(store, "store");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    public CompletionStage<OutboxBatchResult> execute(int batchSize, Instant now) {
        if (batchSize < 1) {
            throw new com.alexastudillo.partyregistry.application.ApplicationFailure(
                    "VALIDATION_ERROR", "Outbox batch size must be positive");
        }
        UseCaseSupport.required(now, "now");
        return store.claimEligible(batchSize, now).thenCompose(claims -> process(List.copyOf(claims), 0, 0));
    }

    private CompletionStage<OutboxBatchResult> process(List<OutboxClaim> claims, int index, int stale) {
        if (index == claims.size()) {
            return CompletableFuture.completedFuture(new OutboxBatchResult(claims.size(), index, stale));
        }
        OutboxClaim claim = claims.get(index);
        return publisher.publish(claim)
                .thenCompose(outcome -> store.recordOutcome(
                        claim.eventId(), claim.claimedVersion(), recorded(outcome)))
                .thenCompose(recorded -> process(claims, index + 1, stale + (recorded ? 0 : 1)));
    }

    private static RecordedOutboxOutcome recorded(PublicationOutcome outcome) {
        return switch (outcome) {
            case CONFIRMED -> RecordedOutboxOutcome.PUBLISHED;
            case NON_RECOVERABLE_FAILURE -> RecordedOutboxOutcome.FAILED;
            case TRANSIENT_FAILURE, UNKNOWN -> RecordedOutboxOutcome.PENDING;
        };
    }
}
