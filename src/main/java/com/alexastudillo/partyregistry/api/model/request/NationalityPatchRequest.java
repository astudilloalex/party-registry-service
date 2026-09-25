package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDate;
import java.util.Objects;

/** Distinguishes omitted nationality bounds from explicitly cleared bounds. */
@JsonDeserialize(using = NationalityPatchRequestDeserializer.class)
public record NationalityPatchRequest(FieldUpdate<LocalDate> validFrom, FieldUpdate<LocalDate> validUntil) {

    public NationalityPatchRequest {
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(validUntil, "validUntil");
    }

    public boolean empty() {
        return !validFrom.isPresent() && !validUntil.isPresent();
    }
}
