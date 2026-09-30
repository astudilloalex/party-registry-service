package com.alexastudillo.partyregistry.infrastructure.messaging;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

/**
 * Maps bounded polling, timeout, lease, and retry settings for outbox delivery.
 */
@ConfigMapping(prefix = "party-registry.outbox.publisher")
public interface OutboxPublisherConfiguration {

    @WithDefault("5s")
    Duration pollInterval();

    @WithDefault("25")
    int batchSize();

    @WithDefault("5s")
    Duration publishTimeout();

    @WithDefault("30s")
    Duration claimLease();

    @WithDefault("1s")
    Duration initialBackoff();

    @WithDefault("5m")
    Duration maximumBackoff();

    @WithDefault("10")
    int maximumAttempts();
}
