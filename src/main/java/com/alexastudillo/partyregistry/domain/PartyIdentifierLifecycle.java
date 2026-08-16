package com.alexastudillo.partyregistry.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PartyIdentifierLifecycle {
    private static final Map<PartyIdentifierStatus, Set<PartyIdentifierStatus>> TRANSITIONS = Map.of(
            PartyIdentifierStatus.PENDING_VERIFICATION,
                    Set.of(PartyIdentifierStatus.VERIFIED, PartyIdentifierStatus.REJECTED, PartyIdentifierStatus.REVOKED),
            PartyIdentifierStatus.VERIFIED,
                    Set.of(PartyIdentifierStatus.EXPIRED, PartyIdentifierStatus.REVOKED),
            PartyIdentifierStatus.REJECTED, Set.of(PartyIdentifierStatus.REVOKED),
            PartyIdentifierStatus.EXPIRED, Set.of(PartyIdentifierStatus.REVOKED),
            PartyIdentifierStatus.REVOKED, Set.of());

    private PartyIdentifierLifecycle() {}

    public static PartyIdentifierStatus initialStatus() {
        return PartyIdentifierStatus.PENDING_VERIFICATION;
    }

    public static PartyIdentifierStatus transition(
            PartyIdentifierStatus source,
            PartyIdentifierStatus target,
            LocalDate issuedOn,
            LocalDate expiresOn,
            Instant verifiedAt,
            String verifiedBy) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (!TRANSITIONS.get(source).contains(target)) {
            throw new DomainViolation(
                    "Party Identifier transition from " + source + " to " + target + " is not permitted");
        }
        if (target == PartyIdentifierStatus.VERIFIED
                && (verifiedAt == null || verifiedBy == null || verifiedBy.isBlank())) {
            throw new DomainViolation("A verified identifier requires verifier and verification time");
        }
        if (target == PartyIdentifierStatus.EXPIRED && expiresOn == null) {
            throw new DomainViolation("An expired identifier requires an expiry date");
        }
        if (issuedOn != null && expiresOn != null && expiresOn.isBefore(issuedOn)) {
            throw new DomainViolation("Identifier expiry date must not precede its issue date");
        }
        return target;
    }
}
