package com.alexastudillo.partyregistry.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PartyLifecycle {
    private static final Map<PartyStatus, Set<PartyStatus>> TRANSITIONS = Map.of(
            PartyStatus.DRAFT, Set.of(PartyStatus.ACTIVE, PartyStatus.ARCHIVED),
            PartyStatus.ACTIVE, Set.of(PartyStatus.INACTIVE, PartyStatus.ARCHIVED),
            PartyStatus.INACTIVE, Set.of(PartyStatus.ACTIVE, PartyStatus.ARCHIVED),
            PartyStatus.ARCHIVED, Set.of());

    private PartyLifecycle() {}

    public static PartyStatus initialStatus() {
        return PartyStatus.DRAFT;
    }

    public static PartyStatus transition(PartyStatus source, PartyStatus target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (!TRANSITIONS.get(source).contains(target)) {
            throw new DomainViolation("Party transition from " + source + " to " + target + " is not permitted");
        }
        return target;
    }
}
