package com.alexastudillo.partyregistry.application.party.port.in;

import com.alexastudillo.partyregistry.application.party.command.CreatePartyCommand;
import com.alexastudillo.partyregistry.application.party.command.TrustedCreationContext;
import com.alexastudillo.partyregistry.application.party.result.CreatePartyResult;
import io.smallrye.mutiny.Uni;

public interface CreatePartyUseCase {

    Uni<CreatePartyResult> create(CreatePartyCommand command, TrustedCreationContext context);
}
