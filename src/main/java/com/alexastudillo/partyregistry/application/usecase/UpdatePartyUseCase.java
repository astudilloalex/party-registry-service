package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.MutationResult;
import com.alexastudillo.partyregistry.application.OutboxIntent;
import com.alexastudillo.partyregistry.application.PartyMutationIntent;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.UpdatePartyCommand;
import com.alexastudillo.partyregistry.application.port.PartyUnitOfWorkPort;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class UpdatePartyUseCase {
    private final PartyUnitOfWorkPort unitOfWork;

    public UpdatePartyUseCase(PartyUnitOfWorkPort unitOfWork) {
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public CompletionStage<MutationResult> execute(
            RequestContext context, UUID partyId, UpdatePartyCommand command, Long expectedVersion) {
        long version = UseCaseSupport.expectedVersion(expectedVersion);
        UseCaseSupport.required(context, "context");
        UseCaseSupport.required(partyId, "partyId");
        UseCaseSupport.required(command, "command");
        return unitOfWork.updatePartyAndAppendOutbox(new PartyMutationIntent(
                context.tenantId(), partyId, version, command, context.userId(),
                new OutboxIntent("party.updated.v1", context.processId())));
    }
}
