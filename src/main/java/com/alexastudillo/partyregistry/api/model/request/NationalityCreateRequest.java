package com.alexastudillo.partyregistry.api.model.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/** Carries strictly bound nationality create input before field-specific validation. */
@JsonDeserialize(using = NationalityCreateRequestDeserializer.class)
public record NationalityCreateRequest(
        @Nullable String countryCode, boolean isPrimary,
        @Nullable LocalDate validFrom, @Nullable LocalDate validUntil) {
}
