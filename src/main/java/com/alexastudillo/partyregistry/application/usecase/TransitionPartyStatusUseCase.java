package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.MutationResult;
import com.alexastudillo.partyregistry.application.OutboxIntent;
import com.alexastudillo.partyregistry.application.PartyMutationIntent;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.PartyUnitOfWorkPort;
import com.alexastudillo.partyregistry.domain.PartyLifecycle;
import com.alexastudillo.partyregistry.domain.PartyStatus;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class TransitionPartyStatusUseCase {
    private final PartyUnitOfWorkPort unitOfWork;

    public TransitionPartyStatusUseCase(PartyUnitOfWorkPort unitOfWork) {
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public CompletionStage<MutationResult> execute(
            RequestContext context, UUID partyId, PartyStatus source, PartyStatus target, Long expectedVersion) {
        long version = UseCaseSupport.expectedVersion(expectedVersion);
        PartyStatus accepted = PartyLifecycle.transition(source, target);
        String eventType = switch (accepted) {
            case ACTIVE -> "party.activated.v1";
            case INACTIVE -> "party.inactivated.v1";
            case ARCHIVED -> "party.archived.v1";
            case DRAFT -> throw new IllegalStateException("DRAFT is not a transition target");
        };
        return unitOfWork.transitionPartyAndAppendOutbox(new PartyMutationIntent(
                context.tenantId(), partyId, version, accepted, context.userId(),
                new OutboxIntent(eventType, context.processId())));
    }
}
