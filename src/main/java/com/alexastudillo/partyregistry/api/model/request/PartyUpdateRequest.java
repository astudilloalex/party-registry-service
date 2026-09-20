package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.normalization.PartyTextNormalization;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Binds the sole root PATCH property while retaining blank input for business validation after the version check. */
@RegisterForReflection
@JsonDeserialize(using = PartyUpdateRequestDeserializer.class)
public record PartyUpdateRequest(
        @Nullable @NotNull(message = "display-name-required")
        @Size(max = 300, message = "display-name-too-long") String displayName) {

    /** Validates the normalized UTF-16 length without replacing the original command input. */
    public PartyUpdateRequest normalizedForValidation() {
        return new PartyUpdateRequest(PartyTextNormalization.uppercase(displayName));
    }
}
