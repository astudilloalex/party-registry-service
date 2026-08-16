package com.alexastudillo.partyregistry.application;

import java.util.UUID;

public record RecoveredOutboxEvent(UUID eventId, long version, String eventType, String payload) {}
