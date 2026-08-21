package com.alexastudillo.partyregistry.application;

import java.time.Instant;
import java.util.Objects;

public record AuditFacts(Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {
    public AuditFacts {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
    }
}
