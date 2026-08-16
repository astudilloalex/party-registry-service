package com.alexastudillo.partyregistry.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class NationalityPolicy {
    private NationalityPolicy() {}

    public static void validate(PartyType partyType, List<Nationality> nationalities) {
        Objects.requireNonNull(partyType, "partyType");
        Objects.requireNonNull(nationalities, "nationalities");
        if (partyType != PartyType.NATURAL_PERSON && !nationalities.isEmpty()) {
            throw new DomainViolation("Only a natural-person Party may have nationalities");
        }

        Set<CountryCode> activeCountries = new HashSet<>();
        int activePrimaryCount = 0;
        for (Nationality nationality : nationalities) {
            Objects.requireNonNull(nationality, "nationality");
            if (!nationality.isActive()) {
                continue;
            }
            if (!activeCountries.add(nationality.countryCode())) {
                throw new DomainViolation("A Party cannot have duplicate active nationalities for one country");
            }
            if (nationality.primary() && ++activePrimaryCount > 1) {
                throw new DomainViolation("A Party cannot have more than one active primary nationality");
            }
        }
    }
}
