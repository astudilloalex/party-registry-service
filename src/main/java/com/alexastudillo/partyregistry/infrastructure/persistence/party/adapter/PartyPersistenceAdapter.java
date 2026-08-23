package com.alexastudillo.partyregistry.infrastructure.persistence.party.adapter;

import java.util.Optional;

import org.hibernate.reactive.mutiny.Mutiny;

import com.alexastudillo.partyregistry.application.party.command.PartyCreationEventIntent;
import com.alexastudillo.partyregistry.application.party.port.out.CreatePartyPersistencePort;
import com.alexastudillo.partyregistry.application.party.result.CreatePartyResult;
import com.alexastudillo.partyregistry.domain.party.model.Party;

import io.smallrye.mutiny.Uni;

public final class PartyPersistenceAdapter implements CreatePartyPersistencePort {

    private final Mutiny.SessionFactory sessionFactory;

    public PartyPersistenceAdapter(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Uni<CreatePartyResult> persist(
            Party party,
            Optional<PartyCreationEventIntent> eventIntent) {
        return Uni.createFrom().failure(new UnsupportedOperationException("Party persistence is not implemented"));
    }
}
