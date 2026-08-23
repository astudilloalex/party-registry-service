package com.alexastudillo.partyregistry.application.party.port.out;

import com.alexastudillo.partyregistry.application.party.command.PartyCreationEventIntent;
import com.alexastudillo.partyregistry.application.party.result.CreatePartyResult;
import com.alexastudillo.partyregistry.domain.party.model.Party;
import io.smallrye.mutiny.Uni;
import java.util.Optional;

public interface CreatePartyPersistencePort {

    Uni<CreatePartyResult> persist(
            Party party,
            Optional<PartyCreationEventIntent> eventIntent);
}
