package com.alexastudillo.partyregistry.application;

import com.alexastudillo.partyregistry.domain.PartyIdentifierStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public sealed interface IdentifierMutation
        permits IdentifierMutation.Creation,
                IdentifierMutation.LegacyCreation,
                IdentifierMutation.Update,
                IdentifierMutation.Transition {

    record Creation(
            String issuerCode,
            boolean primary,
            LocalDate issuedOn,
            LocalDate expiresOn,
            int normalizationVersion)
            implements IdentifierMutation {
        public Creation {
            if (normalizationVersion < 1) {
                throw new IllegalArgumentException("normalizationVersion must be positive");
            }
        }
    }

    record LegacyCreation() implements IdentifierMutation {}

    record Update(String issuerCode, boolean primary, LocalDate issuedOn, LocalDate expiresOn)
            implements IdentifierMutation {}

    record Transition(
            PartyIdentifierStatus status,
            Instant verifiedAt,
            String verifiedBy,
            LocalDate expiresOn)
            implements IdentifierMutation {
        public Transition {
            Objects.requireNonNull(status, "status");
        }
    }
}
