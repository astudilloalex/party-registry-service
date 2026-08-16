package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.NationalitySearchQuery;
import com.alexastudillo.partyregistry.application.NationalityView;
import com.alexastudillo.partyregistry.application.PageResult;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class SearchNationalitiesUseCase {
    private final PartyQueryPort parties;

    public SearchNationalitiesUseCase(PartyQueryPort parties) {
        this.parties = Objects.requireNonNull(parties, "parties");
    }

    public CompletionStage<PageResult<NationalityView>> execute(
            RequestContext context, UUID partyId, NationalitySearchQuery query) {
        UseCaseSupport.required(query, "query");
        return parties.searchNationalities(
                context.tenantId(), partyId, query.countryCode(), query.primary(), query.active(), query.page());
    }
}
