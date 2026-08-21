package com.alexastudillo.partyregistry.application;

import com.alexastudillo.partyregistry.domain.DetailKind;
import com.alexastudillo.partyregistry.domain.PartyType;
import java.util.Objects;

public record PartyCreationMutation(PartyType type, PartyDetailsMutation details) {
    public PartyCreationMutation {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(details, "details");
        if (details.details().kind() != expectedKind(type)) {
            throw new ApplicationFailure("VALIDATION_ERROR", "Party type and detail kind must match");
        }
    }

    public static PartyCreationMutation from(PartyType type, PartyDetailsView details) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(details, "details");
        if (details.kind() != expectedKind(type)) {
            throw new ApplicationFailure("VALIDATION_ERROR", "Party type and detail kind must match");
        }
        return new PartyCreationMutation(type, PartyDetailsMutation.from(details));
    }

    private static DetailKind expectedKind(PartyType type) {
        return switch (type) {
            case NATURAL_PERSON -> DetailKind.NATURAL_PERSON;
            case LEGAL_ENTITY -> DetailKind.LEGAL_ENTITY;
        };
    }
}
