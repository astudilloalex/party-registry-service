package com.alexastudillo.partyregistry.application;

import java.time.LocalDate;

public record UpdateIdentifierCommand(
        String issuerCode, boolean primary, LocalDate issuedOn, LocalDate expiresOn) {}
