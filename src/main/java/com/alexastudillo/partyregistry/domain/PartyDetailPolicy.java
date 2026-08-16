package com.alexastudillo.partyregistry.domain;

import java.util.Objects;

public final class PartyDetailPolicy {
    private PartyDetailPolicy() {}

    public static void validate(PartyType partyType, DetailKind detailKind) {
        Objects.requireNonNull(partyType, "partyType");
        Objects.requireNonNull(detailKind, "detailKind");
        if (!partyType.name().equals(detailKind.name())) {
            throw new DomainViolation("Party type and detail kind must match");
        }
    }
}
