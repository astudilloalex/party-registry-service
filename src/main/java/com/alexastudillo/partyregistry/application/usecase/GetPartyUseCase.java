package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.PartyView;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class GetPartyUseCase {
    private final PartyQueryPort parties;

    public GetPartyUseCase(PartyQueryPort parties) {
        this.parties = Objects.requireNonNull(parties, "parties");
    }

    public CompletionStage<PartyView> execute(RequestContext context, UUID partyId) {
        return parties.findByTenantAndId(context.tenantId(), UseCaseSupport.required(partyId, "partyId"))
                .thenApply(party -> UseCaseSupport.found(party, "Party"));
    }
}
