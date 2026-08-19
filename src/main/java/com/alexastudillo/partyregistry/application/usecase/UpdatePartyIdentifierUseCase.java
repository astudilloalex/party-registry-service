package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.IdentifierMutation;
import com.alexastudillo.partyregistry.application.IdentifierMutationIntent;
import com.alexastudillo.partyregistry.application.MutationResult;
import com.alexastudillo.partyregistry.application.OutboxIntent;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.UpdateIdentifierCommand;
import com.alexastudillo.partyregistry.application.port.IdentifierUnitOfWorkPort;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class UpdatePartyIdentifierUseCase {
    private final IdentifierUnitOfWorkPort unitOfWork;

    public UpdatePartyIdentifierUseCase(IdentifierUnitOfWorkPort unitOfWork) {
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public CompletionStage<MutationResult> execute(
            RequestContext context,
            UUID identifierId,
            UUID partyId,
            UUID schemeId,
            UpdateIdentifierCommand command,
            Long expectedVersion) {
        long version = UseCaseSupport.expectedVersion(expectedVersion);
        UseCaseSupport.required(command, "command");
        return unitOfWork.updateIdentifierAndAppendOutbox(new IdentifierMutationIntent(
                context.tenantId(), identifierId, partyId, schemeId, null, version,
                new IdentifierMutation.Update(
                        command.issuerCode(), command.primary(), command.issuedOn(), command.expiresOn()),
                context.userId(),
                new OutboxIntent("party.identifier-updated.v1", context.processId())));
    }
}
