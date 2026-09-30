package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.smallrye.config.ConfigMapping;

import java.time.Duration;

/**
 * Supplies finite server-statement and complete collection-traversal budgets for root queries.
 */
@ConfigMapping(prefix = "party-registry.queries")
public interface PartyQueryConfiguration {

    Duration statementTimeout();

    Duration traversalTimeout();
}
