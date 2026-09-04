package com.alexastudillo.partyregistry.application.model;

import java.util.Objects;

/**
 * Combines the natural-person creation result with its idempotency outcome.
 */
public record IdempotentCreationResult(
        NaturalPersonResult result,
        IdempotentCreationOutcome outcome) {

    public IdempotentCreationResult {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(outcome, "outcome");
    }
}
