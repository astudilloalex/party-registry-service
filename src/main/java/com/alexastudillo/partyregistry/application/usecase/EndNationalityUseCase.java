package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.MutationResult;
import com.alexastudillo.partyregistry.application.NationalityMutationIntent;
import com.alexastudillo.partyregistry.application.OutboxIntent;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.PartyUnitOfWorkPort;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class EndNationalityUseCase {
    private final PartyUnitOfWorkPort unitOfWork;

    public EndNationalityUseCase(PartyUnitOfWorkPort unitOfWork) {
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    }

    public CompletionStage<MutationResult> execute(
            RequestContext context,
            UUID partyId,
            UUID nationalityId,
            String existingCountryCode,
            LocalDate validFrom,
            LocalDate validUntil,
            Long expectedVersion) {
        long version = UseCaseSupport.expectedVersion(expectedVersion);
        return unitOfWork.endNationalityAndAppendOutbox(new NationalityMutationIntent(
                context.tenantId(), partyId, nationalityId, existingCountryCode, null, validFrom, validUntil,
                null, version, context.userId(),
                new OutboxIntent("party.nationality-removed.v1", context.processId())));
    }
}
