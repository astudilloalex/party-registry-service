package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.IdentifierMutation;
import com.alexastudillo.partyregistry.application.IdentifierMutationIntent;
import com.alexastudillo.partyregistry.application.IdentifierTransitionCommand;
import com.alexastudillo.partyregistry.application.MutationResult;
import com.alexastudillo.partyregistry.application.OutboxIntent;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.IdentifierUnitOfWorkPort;
import com.alexastudillo.partyregistry.domain.PartyIdentifierLifecycle;
import com.alexastudillo.partyregistry.domain.PartyIdentifierStatus;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class TransitionPartyIdentifierStatusUseCase {
    private final IdentifierUnitOfWorkPort unitOfWork;

    public TransitionPartyIdentifierStatusUseCase(IdentifierUnitOfWorkPort unitOfWork) {
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public CompletionStage<MutationResult> execute(
            RequestContext context,
            UUID identifierId,
            UUID partyId,
            UUID schemeId,
            IdentifierTransitionCommand command,
            Long expectedVersion) {
        long version = UseCaseSupport.expectedVersion(expectedVersion);
        UseCaseSupport.required(command, "command");
        PartyIdentifierStatus accepted = PartyIdentifierLifecycle.transition(
                command.source(), command.target(), command.issuedOn(), command.expiresOn(),
                command.verifiedAt(), command.verifiedBy());
        String eventType = switch (accepted) {
            case VERIFIED -> "party.identifier-verified.v1";
            case REJECTED -> "party.identifier-rejected.v1";
            case EXPIRED -> "party.identifier-expired.v1";
            case REVOKED -> "party.identifier-revoked.v1";
            case PENDING_VERIFICATION -> throw new IllegalStateException("PENDING_VERIFICATION is not a transition target");
        };
        return unitOfWork.transitionIdentifierAndAppendOutbox(new IdentifierMutationIntent(
                context.tenantId(), identifierId, partyId, schemeId, null, version,
                new IdentifierMutation.Transition(
                        accepted, command.verifiedAt(), command.verifiedBy(), command.expiresOn()),
                context.userId(),
                new OutboxIntent(eventType, context.processId())));
    }
}
