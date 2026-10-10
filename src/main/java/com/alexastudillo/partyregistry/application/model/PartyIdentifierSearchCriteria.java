package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;
import com.alexastudillo.partyregistry.domain.model.PartyIdentifierStatus;
import org.jspecify.annotations.Nullable;

/** Carries exact AND-combined filters, including a tri-state primary choice and effective page capacity. */
public record PartyIdentifierSearchCriteria(@Nullable IdentifierSchemeId identifierSchemeId,
        @Nullable String schemeCode, @Nullable PartyIdentifierStatus status, @Nullable Boolean isPrimary, int limit) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAXIMUM_LIMIT = 200;

    public PartyIdentifierSearchCriteria {
        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException("Identifier page size is outside the supported range");
        }
        if (schemeCode != null && (schemeCode.isBlank() || schemeCode.codePointCount(0, schemeCode.length()) > 64)) {
            throw new IllegalArgumentException("Identifier scheme filter exceeds its accepted shape bound");
        }
    }
}
