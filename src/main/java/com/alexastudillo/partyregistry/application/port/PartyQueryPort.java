package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.NationalityView;
import com.alexastudillo.partyregistry.application.PageRequest;
import com.alexastudillo.partyregistry.application.PageResult;
import com.alexastudillo.partyregistry.application.PartyDetailsView;
import com.alexastudillo.partyregistry.application.PartyView;
import com.alexastudillo.partyregistry.domain.PartyStatus;
import com.alexastudillo.partyregistry.domain.PartyType;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface PartyQueryPort {
    CompletionStage<PartyView> findByTenantAndId(UUID tenantId, UUID partyId);

    default CompletionStage<PartyView> findById(UUID partyId) {
        throw new UnsupportedOperationException("Global Party lookup is prohibited");
    }

    CompletionStage<PageResult<PartyView>> search(
            UUID tenantId, PartyType type, PartyStatus status, PageRequest pageRequest);

    CompletionStage<PartyDetailsView> findDetails(UUID tenantId, UUID partyId);

    CompletionStage<NationalityView> findNationality(UUID tenantId, UUID partyId, UUID nationalityId);

    CompletionStage<PageResult<NationalityView>> searchNationalities(
            UUID tenantId,
            UUID partyId,
            String countryCode,
            Boolean primary,
            Boolean active,
            PageRequest pageRequest);
}
