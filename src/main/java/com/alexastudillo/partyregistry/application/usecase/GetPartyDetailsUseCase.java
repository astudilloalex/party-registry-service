package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.PartyDetailsView;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class GetPartyDetailsUseCase {
    private final PartyQueryPort parties;

    public GetPartyDetailsUseCase(PartyQueryPort parties) {
        this.parties = Objects.requireNonNull(parties, "parties");
    }

    public CompletionStage<PartyDetailsView> execute(RequestContext context, UUID partyId) {
        return parties.findDetails(context.tenantId(), partyId)
                .thenApply(details -> UseCaseSupport.found(details, "Party details"));
    }
}
