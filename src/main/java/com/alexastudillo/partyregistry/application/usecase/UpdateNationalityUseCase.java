package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.MutationResult;
import com.alexastudillo.partyregistry.application.NationalityCommand;
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
            NationalityCommand command,
            Long expectedVersion) {
        UseCaseSupport.required(command, "command");
        return execute(context, partyId, nationalityId, command.countryCode(), command.primary(), command.validFrom(),
                command.validUntil(), expectedVersion);
    }

    public CompletionStage<MutationResult> execute(
            RequestContext context,
            UUID partyId,
            UUID nationalityId,
            String countryCode,
            LocalDate validFrom,
            LocalDate validUntil,
            Long expectedVersion) {
        return execute(context, partyId, nationalityId, countryCode, null, validFrom, validUntil, expectedVersion);
    }

    private CompletionStage<MutationResult> execute(
            RequestContext context,
            UUID partyId,
            UUID nationalityId,
            String countryCode,
            Boolean primary,
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
                        primary,
                        validFrom,
                        validUntil,
                        UseCaseSupport.found(evidence, "Active country"),
                        version,
                        context.userId(),
                        new OutboxIntent("party.nationality-updated.v1", context.processId()))));
    }
}
