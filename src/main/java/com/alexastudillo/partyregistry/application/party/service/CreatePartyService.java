package com.alexastudillo.partyregistry.application.party.service;

import com.alexastudillo.partyregistry.application.party.command.CreatePartyCommand;
import com.alexastudillo.partyregistry.application.party.command.TrustedCreationContext;
import com.alexastudillo.partyregistry.application.party.port.in.CreatePartyUseCase;
import com.alexastudillo.partyregistry.application.party.port.out.ActiveCountryReferenceValidationPort;
import com.alexastudillo.partyregistry.application.party.port.out.CreatePartyPersistencePort;
import com.alexastudillo.partyregistry.application.party.port.out.PartyEventPolicy;
import com.alexastudillo.partyregistry.application.party.port.out.TimeProvider;
import com.alexastudillo.partyregistry.application.party.result.CreatePartyResult;

import io.smallrye.mutiny.Uni;

/**
 * Application service boundary for Party creation.
 */
public final class CreatePartyService implements CreatePartyUseCase {

    private final ActiveCountryReferenceValidationPort countryValidationPort;
    private final CreatePartyPersistencePort persistencePort;
    private final PartyEventPolicy eventPolicy;
    private final TimeProvider timeProvider;

    public CreatePartyService(
            ActiveCountryReferenceValidationPort countryValidationPort,
            CreatePartyPersistencePort persistencePort,
            PartyEventPolicy eventPolicy,
            TimeProvider timeProvider) {
        this.countryValidationPort = countryValidationPort;
        this.persistencePort = persistencePort;
        this.eventPolicy = eventPolicy;
        this.timeProvider = timeProvider;
    }

    @Override
    public Uni<CreatePartyResult> create(CreatePartyCommand command, TrustedCreationContext context) {
        return Uni.createFrom().failure(new UnsupportedOperationException("Party creation is not implemented"));
    }
}
