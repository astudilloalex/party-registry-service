package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.LegalEntity;
import com.alexastudillo.partyregistry.domain.model.NaturalPerson;
import com.alexastudillo.partyregistry.domain.model.Party;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import com.alexastudillo.partyregistry.domain.model.PartyRecordStatus;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.PartyVersion;
import com.alexastudillo.partyregistry.domain.model.TenantId;

import java.time.Instant;
import java.util.Objects;

/**
 * Defines the safe common Party data returned by application operations.
 */
public sealed interface PartyDetailsResult permits NaturalPersonResult, LegalEntityResult {

    PartyId partyId();

    TenantId tenantId();

    PartyType type();

    String displayName();

    PartyRecordStatus recordStatus();

    PartyVersion version();

    Instant createdAt();

    String createdBy();

    Instant updatedAt();

    String updatedBy();

    /**
     * Projects a Party aggregate into its matching application result.
     *
     * @param party aggregate to project
     * @return type-specific Party details
     */
    static PartyDetailsResult fromAggregate(Party party) {
        Objects.requireNonNull(party, "party");
        return switch (party) {
            case NaturalPerson naturalPerson -> NaturalPersonResult.fromAggregate(naturalPerson);
            case LegalEntity legalEntity -> LegalEntityResult.fromAggregate(legalEntity);
        };
    }
}
