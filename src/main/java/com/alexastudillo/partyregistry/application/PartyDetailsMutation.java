package com.alexastudillo.partyregistry.application;

import com.alexastudillo.partyregistry.domain.DetailKind;
import com.alexastudillo.partyregistry.domain.DisplayName;
import java.util.Objects;

public record PartyDetailsMutation(PartyDetailsView details, String canonicalDisplayName) {
    public PartyDetailsMutation {
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(canonicalDisplayName, "canonicalDisplayName");
        if (!canonicalDisplayName.equals(derive(details))) {
            throw new ApplicationFailure(
                    "VALIDATION_ERROR", "Canonical display name must match its Party detail sources");
        }
    }

    public static PartyDetailsMutation from(PartyDetailsView details) {
        return new PartyDetailsMutation(details, derive(details));
    }

    private static String derive(PartyDetailsView details) {
        Objects.requireNonNull(details, "details");
        DetailKind kind = details.kind();
        if (kind == null) {
            throw new ApplicationFailure("VALIDATION_ERROR", "Detail kind is required");
        }
        return switch (kind) {
            case NATURAL_PERSON -> DisplayName.forNaturalPerson(details.primaryName(), details.secondaryName());
            case LEGAL_ENTITY -> DisplayName.forLegalEntity(details.primaryName());
        };
    }
}
