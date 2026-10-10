package com.alexastudillo.partyregistry.application.query;

import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchCriteria;
import com.alexastudillo.partyregistry.application.model.PartyIdentifierSearchScope;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.domain.model.PartyId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Requests an owned historical identifier page with exact filters and an optional opaque continuation. */
public record ListPartyIdentifiersQuery(RequestMetadata requestMetadata, PartyId partyId,
        PartyIdentifierSearchCriteria criteria, @Nullable String cursor) {

    public ListPartyIdentifiersQuery {
        Objects.requireNonNull(requestMetadata, "requestMetadata");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(criteria, "criteria");
        if (cursor != null && (cursor.isBlank() || cursor.length() > 256)) {
            throw new IllegalArgumentException("Identifier continuation exceeds its accepted shape bound");
        }
    }

    public PartyIdentifierSearchScope scope() {
        return new PartyIdentifierSearchScope(requestMetadata.tenantId(), partyId, criteria);
    }

    @Override
    public String toString() {
        return "ListPartyIdentifiersQuery[cursor=<redacted>]";
    }
}
