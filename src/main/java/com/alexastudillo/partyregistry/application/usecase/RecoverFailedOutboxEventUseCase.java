package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.RecoveredOutboxEvent;
import com.alexastudillo.partyregistry.application.port.OutboxStorePort;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class RecoverFailedOutboxEventUseCase {
    private final OutboxStorePort store;

    public RecoverFailedOutboxEventUseCase(OutboxStorePort store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public CompletionStage<RecoveredOutboxEvent> execute(UUID eventId, long expectedVersion) {
        UseCaseSupport.required(eventId, "eventId");
        if (expectedVersion < 0) {
            throw new com.alexastudillo.partyregistry.application.ApplicationFailure(
                    "VALIDATION_ERROR", "Outbox version must be non-negative");
        }
        return store.recoverFailed(eventId, expectedVersion);
    }
}
