package com.alexastudillo.partyregistry.application;

import java.time.LocalDate;

public record NationalityCommand(String countryCode, boolean primary, LocalDate validFrom, LocalDate validUntil) {}
