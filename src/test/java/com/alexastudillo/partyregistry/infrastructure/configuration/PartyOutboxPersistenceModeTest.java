package com.alexastudillo.partyregistry.infrastructure.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the narrow registration-time bridge to the future outbox mode policy.
 */
class PartyOutboxPersistenceModeTest {

    @Test
    void storesOnlyEventsEnabledByTheConfiguredMode() {
        assertFalse(PartyOutboxPersistenceMode.fromConfiguration("disabled").storesEvents());
        assertTrue(PartyOutboxPersistenceMode.fromConfiguration("stored-only").storesEvents());
        assertTrue(PartyOutboxPersistenceMode.fromConfiguration("published").storesEvents());
        assertFalse(PartyOutboxPersistenceMode.fromConfiguration("disabled").publishesEvents());
        assertFalse(PartyOutboxPersistenceMode.fromConfiguration("stored-only").publishesEvents());
        assertTrue(PartyOutboxPersistenceMode.fromConfiguration("published").publishesEvents());
        assertEquals(
                PartyOutboxPersistenceMode.STORED_ONLY,
                PartyOutboxPersistenceMode.fromConfiguration("STORED-ONLY"));
    }

    @Test
    void rejectsMissingOrUnsupportedModes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PartyOutboxPersistenceMode.fromConfiguration(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> PartyOutboxPersistenceMode.fromConfiguration("unexpected"));
    }
}
