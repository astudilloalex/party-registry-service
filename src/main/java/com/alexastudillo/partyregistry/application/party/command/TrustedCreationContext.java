package com.alexastudillo.partyregistry.application.party.command;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TrustedCreationContext(
        UUID tenantId,
        String tenantHeaderValue,
        String userId,
        UUID processId,
        String processHeaderValue,
        Instant occurredAt,
        LocalDate registrationDate) {

    public TrustedCreationContext(
            UUID tenantId,
            String tenantHeaderValue,
            String userId,
            UUID processId,
            String processHeaderValue) {
        this(tenantId, tenantHeaderValue, userId, processId, processHeaderValue, null, null);
    }
}
