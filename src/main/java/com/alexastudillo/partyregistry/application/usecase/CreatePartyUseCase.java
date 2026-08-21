package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.CreatePartyCommand;
import com.alexastudillo.partyregistry.application.MutationResult;
import com.alexastudillo.partyregistry.application.OutboxIntent;
import com.alexastudillo.partyregistry.application.PartyCreationMutation;
import com.alexastudillo.partyregistry.application.PartyMutationIntent;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.GeographicReferencePort;
import com.alexastudillo.partyregistry.application.port.PartyUnitOfWorkPort;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class CreatePartyUseCase {
    private final GeographicReferencePort geography;
    private final PartyUnitOfWorkPort unitOfWork;

    public CreatePartyUseCase(GeographicReferencePort geography, PartyUnitOfWorkPort unitOfWork) {
        this.geography = Objects.requireNonNull(geography, "geography");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public CompletionStage<MutationResult> execute(RequestContext context, CreatePartyCommand command) {
        UseCaseSupport.required(context, "context");
        UseCaseSupport.required(command, "command");
        UseCaseSupport.required(command.type(), "type");
        UseCaseSupport.required(command.details(), "details");
        PartyCreationMutation mutation = PartyCreationMutation.from(command.type(), command.details());
        CompletionStage<?> evidence = command.details().countryCode() == null
                ? CompletableFuture.completedFuture(null)
                : geography.resolveActive(command.details().countryCode())
                        .thenApply(country -> UseCaseSupport.found(country, "Active country"));
        return evidence.thenCompose(ignored -> unitOfWork.createPartyAndAppendOutbox(new PartyMutationIntent(
                context.tenantId(), command.details().partyId(), 0, mutation, context.userId(),
                new OutboxIntent("party.created.v1", context.processId()))));
    }
}
