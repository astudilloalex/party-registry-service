package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.api.response.contract.PaginationMetadata;
import com.alexastudillo.partyregistry.api.model.response.IdentifierSchemeResponse;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemePage;
import com.alexastudillo.partyregistry.application.model.IdentifierSchemeResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Objects;

/** Projects detached scheme results to public DTOs before shared HTTP response construction. */
@ApplicationScoped
public class IdentifierSchemeApiMapper {
    public IdentifierSchemeResponse toResponse(IdentifierSchemeResult result) {
        Objects.requireNonNull(result, "result");
        return new IdentifierSchemeResponse(result.id().value(), result.code(), result.issuingCountryCode(),
                result.category().name(), result.applicableSubjectType().name(), result.name(), result.description(),
                result.normalizerKey(), result.validatorKey(), result.minimumLength(), result.maximumLength(),
                result.requiresExpiration(), result.status().name(), result.version().value(), result.createdAt(), result.updatedAt());
    }

    public List<IdentifierSchemeResponse> toResponses(IdentifierSchemePage page) {
        Objects.requireNonNull(page, "page");
        return page.items().stream().map(this::toResponse).toList();
    }

    /** Supplies only available navigation and the actual page size; null totals retain NON_NULL cursor omission. */
    public PaginationMetadata toPagination(IdentifierSchemePage page) {
        Objects.requireNonNull(page, "page");
        return new PaginationMetadata(page.nextCursor().orElse(null), page.previousCursor().orElse(null),
                null, null, page.items().size());
    }
}
