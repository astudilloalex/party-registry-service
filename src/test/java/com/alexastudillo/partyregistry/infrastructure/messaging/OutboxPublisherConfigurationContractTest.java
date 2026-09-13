package com.alexastudillo.partyregistry.infrastructure.messaging;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins scheduler exclusion, confirmed delivery, and secret-free production broker settings.
 */
class OutboxPublisherConfigurationContractTest {

    @Test
    void schedulesAnAsynchronousPipelineAndSkipsConcurrentRuns() throws NoSuchMethodException {
        Method scheduledMethod = PartyOutboxPublisher.class.getDeclaredMethod("publishDueEvents");
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals(Uni.class, scheduledMethod.getReturnType());
        assertEquals(Scheduled.ConcurrentExecution.SKIP, scheduled.concurrentExecution());
        assertEquals(
                "${party-registry.outbox.publisher.poll-interval}",
                scheduled.every());
    }

    @Test
    void requiresPublisherConfirmsAndProductionCredentialsWithoutDefaults() throws IOException {
        Properties properties = applicationProperties();

        assertEquals(
                "smallrye-rabbitmq",
                properties.getProperty("mp.messaging.outgoing.party-outbox.connector"));
        assertEquals(
                "true",
                properties.getProperty("mp.messaging.outgoing.party-outbox.publish-confirms"));
        assertEquals(
                "0",
                properties.getProperty("mp.messaging.outgoing.party-outbox.retry-on-fail-attempts"));
        assertEquals(
                "false",
                properties.getProperty("%test.quarkus.rabbitmq.devservices.enabled"));
        assertEquals("${RABBITMQ_HOST}", properties.getProperty("%prod.rabbitmq-host"));
        assertEquals("${RABBITMQ_USERNAME}", properties.getProperty("%prod.rabbitmq-username"));
        assertEquals("${RABBITMQ_PASSWORD}", properties.getProperty("%prod.rabbitmq-password"));
    }

    private static Properties applicationProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = OutboxPublisherConfigurationContractTest.class
                .getResourceAsStream("/application.properties")) {
            assertNotNull(input);
            properties.load(input);
        }
        return properties;
    }
}
