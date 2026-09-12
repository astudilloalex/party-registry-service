package com.alexastudillo.partyregistry.application.command;

import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyType;
import com.alexastudillo.partyregistry.domain.model.TenantId;

/**
 * Defines the common inputs required to fingerprint and register a Party.
 */
public sealed interface PartyRegistrationCommand
        permits RegisterNaturalPersonCommand, RegisterLegalEntityCommand {

    RequestMetadata requestMetadata();

    String idempotencyKey();

    InitialPartyIdentifierInput initialIdentifier();

    String operation();

    PartyType partyType();

    default TenantId tenantId() {
        return requestMetadata().tenantId();
    }
}
