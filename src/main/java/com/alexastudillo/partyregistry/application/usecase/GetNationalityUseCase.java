package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.NationalityView;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class GetNationalityUseCase {
    private final PartyQueryPort parties;

    public GetNationalityUseCase(PartyQueryPort parties) {
        this.parties = Objects.requireNonNull(parties, "parties");
    }

    public CompletionStage<NationalityView> execute(RequestContext context, UUID partyId, UUID nationalityId) {
        return parties.findNationality(context.tenantId(), partyId, nationalityId)
                .thenApply(value -> UseCaseSupport.found(value, "Nationality"));
    }
}
