package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.MutationResult;
import com.alexastudillo.partyregistry.application.NationalityCommand;
import com.alexastudillo.partyregistry.application.NationalityMutationIntent;
import com.alexastudillo.partyregistry.application.OutboxIntent;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.GeographicReferencePort;
import com.alexastudillo.partyregistry.application.port.PartyUnitOfWorkPort;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class AddNationalityUseCase {
    private final GeographicReferencePort geography;
    private final PartyUnitOfWorkPort unitOfWork;

    public AddNationalityUseCase(GeographicReferencePort geography, PartyUnitOfWorkPort unitOfWork) {
        this.geography = Objects.requireNonNull(geography, "geography");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public CompletionStage<MutationResult> execute(
            RequestContext context, UUID partyId, NationalityCommand command, Long expectedVersion) {
        long version = UseCaseSupport.expectedVersion(expectedVersion);
        UseCaseSupport.required(command, "command");
        return geography.resolveActive(command.countryCode()).thenCompose(evidence ->
                unitOfWork.addNationalityAndAppendOutbox(new NationalityMutationIntent(
                        context.tenantId(), partyId, null, command.countryCode(), command.primary(), command.validFrom(),
                        command.validUntil(), UseCaseSupport.found(evidence, "Active country"), version, context.userId(),
                        new OutboxIntent("party.nationality-added.v1", context.processId()))));
    }
}
