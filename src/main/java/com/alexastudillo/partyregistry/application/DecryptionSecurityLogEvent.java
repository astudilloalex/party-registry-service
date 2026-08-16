package com.alexastudillo.partyregistry.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DecryptionSecurityLogEvent(
        UUID tenantId,
        String userId,
        UUID processId,
        UUID partyIdentifierId,
        Instant timestamp,
        String action,
        String outcome) {
    public DecryptionSecurityLogEvent {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(processId, "processId");
        Objects.requireNonNull(partyIdentifierId, "partyIdentifierId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(outcome, "outcome");
    }
}
