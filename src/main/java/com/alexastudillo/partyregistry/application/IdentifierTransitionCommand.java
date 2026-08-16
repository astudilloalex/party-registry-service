package com.alexastudillo.partyregistry.application;

import com.alexastudillo.partyregistry.domain.PartyIdentifierStatus;
import java.time.Instant;
import java.time.LocalDate;

public record IdentifierTransitionCommand(
        PartyIdentifierStatus source,
        PartyIdentifierStatus target,
        LocalDate issuedOn,
        LocalDate expiresOn,
        Instant verifiedAt,
        String verifiedBy) {}
