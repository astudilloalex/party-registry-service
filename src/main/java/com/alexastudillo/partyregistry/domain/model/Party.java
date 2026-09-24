package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;

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
     * Corrects the display name in any lifecycle state without rewriting historical details.
     *
     * @param value new label, normalized with the established root-locale text rules
     * @param occurredAt modification timestamp
     * @param updatedBy modifying user
     * @return the corrected aggregate at exactly the next version, even for identical canonical input
     * @throws DomainValidationException when the canonical label is blank or exceeds 300 UTF-16
     *                                   units, or the version or audit transition is invalid
     */
    Party correctDisplayName(String value, Instant occurredAt, String updatedBy);

    /**
     * Transitions a draft Party to active and returns the new aggregate state.
     *
     * @param occurredAt transition timestamp
     * @param updatedBy modifying user
     * @return the active aggregate at the next version
     */
    Party activate(Instant occurredAt, String updatedBy);

    /**
     * Deactivates an active Party without changing its identity or independent identifiers.
     *
     * @param occurredAt transition timestamp
     * @param updatedBy modifying user
     * @return the inactive aggregate at the next version
     * @throws DomainValidationException when the current state, next version, or audit transition is invalid
     */
    Party deactivate(Instant occurredAt, String updatedBy);

    /**
     * Archives a draft, active, or inactive Party while retaining its identity history.
     *
     * @param occurredAt transition timestamp
     * @param updatedBy modifying user
     * @return the archived aggregate at the next version
     * @throws DomainValidationException when already archived or the next version or audit transition is invalid
     */
    Party archive(Instant occurredAt, String updatedBy);
}
