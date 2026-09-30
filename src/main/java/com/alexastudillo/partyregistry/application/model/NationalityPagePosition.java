package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.NationalityId;

import java.time.Instant;
import java.util.Objects;

/** Identifies one exact creation-time and nationality-ID keyset position. */
public record NationalityPagePosition(Instant createdAt, NationalityId nationalityId) {

    public NationalityPagePosition {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(nationalityId, "nationalityId");
    }
}
