package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.MutationResult;
import com.alexastudillo.partyregistry.application.OutboxIntent;
import com.alexastudillo.partyregistry.application.PartyDetailsView;
import com.alexastudillo.partyregistry.application.PartyMutationIntent;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.GeographicReferencePort;
import com.alexastudillo.partyregistry.application.port.PartyUnitOfWorkPort;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class UpdatePartyDetailsUseCase {
    private final GeographicReferencePort geography;
    private final PartyUnitOfWorkPort unitOfWork;

    public UpdatePartyDetailsUseCase(GeographicReferencePort geography, PartyUnitOfWorkPort unitOfWork) {
        this.geography = Objects.requireNonNull(geography, "geography");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public CompletionStage<MutationResult> execute(
            RequestContext context, UUID partyId, PartyDetailsView details, Long expectedVersion) {
        long version = UseCaseSupport.expectedVersion(expectedVersion);
        CompletionStage<?> evidence = details.countryCode() == null
                ? CompletableFuture.completedFuture(null)
                : geography.resolveActive(details.countryCode())
                        .thenApply(country -> UseCaseSupport.found(country, "Active country"));
        return evidence.thenCompose(ignored -> unitOfWork.updateDetailsAndAppendOutbox(new PartyMutationIntent(
                context.tenantId(), partyId, version, details, context.userId(),
                new OutboxIntent("party.updated.v1", context.processId()))));
    }
}
