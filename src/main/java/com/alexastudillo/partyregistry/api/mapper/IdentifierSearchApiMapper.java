package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.api.response.contract.PaginationMetadata;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSearchExactRequest;
import com.alexastudillo.partyregistry.api.model.request.IdentifierSearchPrefixRequest;
import com.alexastudillo.partyregistry.api.model.response.IdentifierSearchIdentifierResponse;
import com.alexastudillo.partyregistry.api.model.response.IdentifierSearchResponse;
import com.alexastudillo.partyregistry.application.model.*;
import com.alexastudillo.partyregistry.domain.model.TenantId;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/** Maps strict search inputs and safe Application results before explicit shared HTTP wrapping. */
@ApplicationScoped
public class IdentifierSearchApiMapper {
    public ExactIdentifierSearchQuery toQuery(TenantId tenant, IdentifierSearchExactRequest request) {
        return new ExactIdentifierSearchQuery(tenant, request.identifierSchemeCode(), request.value(),
                IdentifierSearchFilters.of(request.identifierStatuses(), request.partyRecordStatuses()));
    }

    public PrefixIdentifierSearchQuery toQuery(TenantId tenant, IdentifierSearchPrefixRequest request) {
        return new PrefixIdentifierSearchQuery(tenant, request.identifierSchemeCode(), request.prefix(),
                IdentifierSearchFilters.of(request.identifierStatuses(), request.partyRecordStatuses()), request.limit(), request.cursor());
    }

    public List<IdentifierSearchResponse> toResponses(IdentifierSearchPage page) {
        return page.items().stream().map(hit -> new IdentifierSearchResponse(hit.partyId().value(), hit.type().name(), hit.displayName(),
                hit.recordStatus().name(), new IdentifierSearchIdentifierResponse(hit.position().identifierId().value(), hit.schemeCode(),
                hit.maskedValue(), hit.status().name(), hit.primary()))).toList();
    }

    /** Rejects response capacity overflow instead of narrowing, capping or silently omitting complete counts. */
    public PaginationMetadata toPagination(IdentifierSearchPage page) {
        return new PaginationMetadata(page.nextCursor(), page.previousCursor(), Math.toIntExact(page.totalElements()),
                Math.toIntExact(page.totalPages()), page.numberOfElements());
    }
}
