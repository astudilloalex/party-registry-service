package com.alexastudillo.partyregistry.application;

import com.alexastudillo.partyregistry.domain.CountryCode;
import java.time.Instant;
import java.util.Objects;

public record CountryEvidence(String countryCode, Instant observedAt) {
    public CountryEvidence {
        countryCode = CountryCode.of(countryCode).value();
        Objects.requireNonNull(observedAt, "observedAt");
    }
}
