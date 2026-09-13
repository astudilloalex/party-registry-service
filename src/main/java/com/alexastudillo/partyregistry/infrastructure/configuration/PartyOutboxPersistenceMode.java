package com.alexastudillo.partyregistry.infrastructure.configuration;

import java.util.Locale;

/**
 * Selects transactional storage and asynchronous publication behavior for Party events.
 */
public enum PartyOutboxPersistenceMode {
    DISABLED(false, false),
    STORED_ONLY(true, false),
    PUBLISHED(true, true);

    private final boolean storesEvents;
    private final boolean publishesEvents;

    PartyOutboxPersistenceMode(boolean storesEvents, boolean publishesEvents) {
        this.storesEvents = storesEvents;
        this.publishesEvents = publishesEvents;
    }

    public static PartyOutboxPersistenceMode fromConfiguration(String configuredValue) {
        if (configuredValue == null) {
            throw new IllegalArgumentException("Party outbox mode is required");
        }
        return switch (configuredValue.toLowerCase(Locale.ROOT)) {
            case "disabled" -> DISABLED;
            case "stored-only" -> STORED_ONLY;
            case "published" -> PUBLISHED;
            default -> throw new IllegalArgumentException("Unsupported Party outbox mode");
        };
    }

    public boolean storesEvents() {
        return storesEvents;
    }

    public boolean publishesEvents() {
        return publishesEvents;
    }
}
