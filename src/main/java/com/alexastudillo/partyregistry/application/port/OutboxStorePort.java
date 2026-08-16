package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.OutboxClaim;
import com.alexastudillo.partyregistry.application.RecordedOutboxOutcome;
import com.alexastudillo.partyregistry.application.RecoveredOutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface OutboxStorePort {
    CompletionStage<List<OutboxClaim>> claimEligible(int batchSize, Instant now);

    CompletionStage<Boolean> recordOutcome(UUID eventId, long claimedVersion, RecordedOutboxOutcome outcome);

    CompletionStage<RecoveredOutboxEvent> recoverFailed(UUID eventId, long expectedVersion);
}
