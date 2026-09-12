package com.alexastudillo.partyregistry.infrastructure.persistence;

import com.alexastudillo.partyregistry.infrastructure.messaging.OutboxMessageSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the bounded PostgreSQL claim query and persistence-only store boundary.
 */
class HibernateReactiveOutboxDeliveryStoreQueryTest {

    @Test
    void claimsOnlyDuePendingRowsWithDeterministicSkipLockedOrdering() {
        String query = HibernateReactiveOutboxDeliveryStore.CLAIM_QUERY
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertTrue(query.contains("status = 'pending'"));
        assertTrue(query.contains("next_attempt_at is null or next_attempt_at <= :claimedat"));
        assertTrue(query.contains(
                "order by next_attempt_at asc nulls first, created_at asc, id asc"));
        assertTrue(query.contains("limit :batchsize"));
        assertTrue(query.contains("for update skip locked"));
        assertFalse(query.contains("status = 'published'"));
        assertFalse(query.contains("status = 'failed'"));
    }

    @Test
    void storeHasNoBrokerOrSenderCollaborator() {
        assertFalse(Arrays.stream(HibernateReactiveOutboxDeliveryStore.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(OutboxMessageSender.class::equals));
    }
}
