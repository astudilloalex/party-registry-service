package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.PageResult;
import com.alexastudillo.partyregistry.application.PartySearchQuery;
import com.alexastudillo.partyregistry.application.PartyView;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.PartyQueryPort;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public final class SearchPartiesUseCase {
    private final PartyQueryPort parties;

    public SearchPartiesUseCase(PartyQueryPort parties) {
        this.parties = Objects.requireNonNull(parties, "parties");
    }

    public CompletionStage<PageResult<PartyView>> execute(RequestContext context, PartySearchQuery query) {
        UseCaseSupport.required(context, "context");
        UseCaseSupport.required(query, "query");
        return parties.search(context.tenantId(), query.type(), query.status(), query.page());
    }
}
