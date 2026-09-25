package com.alexastudillo.partyregistry.api.mapper;

import com.alexastudillo.api.response.contract.PaginationMetadata;
import com.alexastudillo.partyregistry.api.model.response.NationalityResponse;
import com.alexastudillo.partyregistry.application.model.NationalityPage;
import com.alexastudillo.partyregistry.application.model.NationalityResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Objects;

/** Translates detached nationality application results to explicit API DTOs and pagination metadata. */
@ApplicationScoped
public class NationalityApiMapper {

    public NationalityResponse toResponse(NationalityResult result) {
        Objects.requireNonNull(result, "result");
        return new NationalityResponse(result.nationalityId().value(), result.partyId().value(), result.countryCode(),
                result.isPrimary(), result.validFrom(), result.validUntil(), result.createdAt(), result.updatedAt());
    }

    public List<NationalityResponse> toResponses(NationalityPage page) {
        Objects.requireNonNull(page, "page");
        return page.items().stream().map(this::toResponse).toList();
    }

    /** Preserves the library's explicit null cursors and checked integer count capacity. */
    public PaginationMetadata toPagination(NationalityPage page) {
        Objects.requireNonNull(page, "page");
        return new PaginationMetadata(page.nextCursor().orElse(null), page.previousCursor().orElse(null),
                Math.toIntExact(page.totalElements()), Math.toIntExact(Math.ceilDiv(page.totalElements(), page.limit())),
                page.items().size());
    }
}
