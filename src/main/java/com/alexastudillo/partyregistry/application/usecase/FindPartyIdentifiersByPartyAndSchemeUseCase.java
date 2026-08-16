package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.PartyAndSchemeSelection;
import com.alexastudillo.partyregistry.application.PartyIdentifierResult;
import com.alexastudillo.partyregistry.application.RequestContext;
import com.alexastudillo.partyregistry.application.port.IdentifierQueryPort;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class FindPartyIdentifiersByPartyAndSchemeUseCase {
    private final IdentifierQueryPort identifiers;

    public FindPartyIdentifiersByPartyAndSchemeUseCase(IdentifierQueryPort identifiers) {
        this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
    }

    public CompletionStage<List<PartyIdentifierResult>> execute(
            RequestContext context, UUID partyId, UUID schemeId, PartyAndSchemeSelection selection) {
        UseCaseSupport.required(selection, "selection");
        return identifiers.findByPartyAndScheme(
                        context.tenantId(), partyId, schemeId, selection == PartyAndSchemeSelection.VERIFIED_PRIMARY)
                .thenApply(values -> values.stream().map(PartyIdentifierResult::from).toList());
    }
}
