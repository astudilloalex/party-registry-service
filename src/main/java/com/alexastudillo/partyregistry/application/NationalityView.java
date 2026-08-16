package com.alexastudillo.partyregistry.application;

import java.time.LocalDate;
import java.util.UUID;

public record NationalityView(
        UUID id, UUID partyId, String countryCode, boolean primary, LocalDate validFrom, LocalDate validUntil) {}
