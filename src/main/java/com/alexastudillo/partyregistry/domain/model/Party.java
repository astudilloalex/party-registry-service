package com.alexastudillo.partyregistry.domain.model;

import java.time.Instant;

/**
 * Defines the common immutable identity and lifecycle behavior of Party aggregates.
 */
public sealed interface Party permits NaturalPerson, LegalEntity {

    PartyId partyId();

    TenantId tenantId();

    PartyType type();

    String displayName();

    PartyRecordStatus recordStatus();

    PartyVersion version();

    AuditInfo auditInfo();

    /**
     * Transitions a draft Party to active and returns the new aggregate state.
     *
     * @param occurredAt transition timestamp
     * @param updatedBy modifying user
     * @return the active aggregate at the next version
     */
    Party activate(Instant occurredAt, String updatedBy);
}
