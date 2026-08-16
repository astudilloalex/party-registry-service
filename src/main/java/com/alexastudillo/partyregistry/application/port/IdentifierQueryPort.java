package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.PageRequest;
import com.alexastudillo.partyregistry.application.PageResult;
import com.alexastudillo.partyregistry.application.PartyIdentifierView;
import com.alexastudillo.partyregistry.domain.PartyIdentifierStatus;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface IdentifierQueryPort {
    CompletionStage<PartyIdentifierView> findByTenantAndId(UUID tenantId, UUID identifierId);

    CompletionStage<PartyIdentifierView> findProtectedByTenantAndId(UUID tenantId, UUID identifierId);

    CompletionStage<List<PartyIdentifierView>> findExact(UUID tenantId, UUID schemeId, String normalizedValueHash);

    CompletionStage<PageResult<PartyIdentifierView>> search(
            UUID tenantId,
            UUID partyId,
            UUID schemeId,
            PartyIdentifierStatus status,
            Boolean primary,
            PageRequest pageRequest);

    CompletionStage<List<PartyIdentifierView>> findByPartyAndScheme(
            UUID tenantId, UUID partyId, UUID schemeId, boolean verifiedPrimaryOnly);
}
