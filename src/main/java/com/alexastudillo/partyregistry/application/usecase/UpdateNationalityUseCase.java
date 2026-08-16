package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.MutationResult;
import com.alexastudillo.partyregistry.application.NationalityMutationIntent;
import com.alexastudillo.partyregistry.application.OutboxIntent;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.GeographicReferencePort;
import com.alexastudillo.partyregistry.application.port.PartyUnitOfWorkPort;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class UpdateNationalityUseCase {
    private final GeographicReferencePort geography;
    private final PartyUnitOfWorkPort unitOfWork;

    public UpdateNationalityUseCase(GeographicReferencePort geography, PartyUnitOfWorkPort unitOfWork) {
        this.geography = Objects.requireNonNull(geography, "geography");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public CompletionStage<MutationResult> execute(
            RequestContext context,
            UUID partyId,
            UUID nationalityId,
            String countryCode,
            LocalDate validFrom,
            LocalDate validUntil,
            Long expectedVersion) {
        long version = UseCaseSupport.expectedVersion(expectedVersion);
        UseCaseSupport.required(context, "context");
        UseCaseSupport.required(partyId, "partyId");
        UseCaseSupport.required(nationalityId, "nationalityId");
        UseCaseSupport.nonblank(countryCode, "countryCode");
        return geography.resolveActive(countryCode).thenCompose(evidence -> unitOfWork.updateNationalityAndAppendOutbox(
                new NationalityMutationIntent(
                        context.tenantId(),
                        partyId,
                        nationalityId,
                        countryCode,
                        validFrom,
                        validUntil,
                        UseCaseSupport.found(evidence, "Active country"),
                        version,
                        context.userId(),
                        new OutboxIntent("party.nationality-updated.v1", context.processId()))));
    }
}
