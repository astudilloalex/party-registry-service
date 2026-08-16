package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.IdentifierQueryPort;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class GetPartyIdentifierUseCase {
    private final IdentifierQueryPort identifiers;

    public GetPartyIdentifierUseCase(IdentifierQueryPort identifiers) {
        this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
    }

    public CompletionStage<PartyIdentifierResult> execute(RequestContext context, UUID identifierId) {
        return identifiers.findByTenantAndId(context.tenantId(), identifierId)
                .thenApply(value -> PartyIdentifierResult.from(UseCaseSupport.found(value, "Party Identifier")));
    }
}
