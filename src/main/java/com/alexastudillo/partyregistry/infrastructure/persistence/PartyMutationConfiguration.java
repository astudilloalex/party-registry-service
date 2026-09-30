package com.alexastudillo.partyregistry.infrastructure.persistence;

import io.smallrye.config.ConfigMapping;

import java.time.Duration;

/** Supplies finite transaction-local lock/statement limits and a complete root mutation work budget. */
@ConfigMapping(prefix = "party-registry.mutations")
public interface PartyMutationConfiguration {

    Duration lockTimeout();

    Duration statementTimeout();

    Duration operationTimeout();
}
