package com.alexastudillo.partyregistry.infrastructure.integration.geographic.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents the closed response envelope returned by the Geographic
 * Reference Service.
 *
 * @param status remote HTTP status repeated in the response body
 * @param code   stable remote response code
 * @param data   country data for a successful lookup
 */
@RegisterForReflection
public record CountryReferenceResponse(
        Integer status,
        String code,
        CountryData data) {

    /**
     * Represents country data used to confirm a successful remote reference.
     *
     * @param id              country identifier
     * @param alpha2Code      ISO 3166-1 alpha-2 code
     * @param alpha3Code      ISO 3166-1 alpha-3 code
     * @param numericCode     ISO 3166-1 numeric code
     * @param defaultName     default country name
     * @param officialName    official country name
     * @param independent     whether the country is independent
     * @param status          geographic record lifecycle status
     * @param validFrom       optional first validity date
     * @param validUntil      optional last validity date
     * @param sourceAuthority source authority
     * @param sourceReference optional source reference
     * @param sourceRevision  optional source revision
     * @param createdAt       creation timestamp
     * @param createdBy       creation subject
     * @param updatedAt       last update timestamp
     * @param updatedBy       last update subject
     * @param version         remote record version
     */
    @RegisterForReflection
    public record CountryData(
            UUID id,
            String alpha2Code,
            String alpha3Code,
            String numericCode,
            String defaultName,
            String officialName,
            Boolean independent,
            CountryActivityStatus status,
            LocalDate validFrom,
            LocalDate validUntil,
            String sourceAuthority,
            String sourceReference,
            String sourceRevision,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy,
            Long version) {
    }

    /**
     * Lists lifecycle values accepted by the remote country contract.
     */
    @RegisterForReflection
    public enum CountryActivityStatus {
        ACTIVE,
        DRAFT,
        DEPRECATED,
        RETIRED
    }
}
