package com.alexastudillo.partyregistry.application.party.port.out;

import com.alexastudillo.partyregistry.application.party.result.CountryValidationOutcome;
import com.alexastudillo.partyregistry.domain.party.model.CountryCode;
import io.smallrye.mutiny.Uni;
import java.util.Set;

public interface ActiveCountryReferenceValidationPort {

    Uni<CountryValidationOutcome> validateAll(
            Set<CountryCode> activeCountryCodes,
            String tenantHeaderValue,
            String auditSubject,
            String processHeaderValue);
}
