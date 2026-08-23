package com.alexastudillo.partyregistry.infrastructure.integration.geographic.adapter;

import java.util.Set;

import com.alexastudillo.partyregistry.application.party.port.out.ActiveCountryReferenceValidationPort;
import com.alexastudillo.partyregistry.application.party.result.CountryValidationOutcome;
import com.alexastudillo.partyregistry.domain.party.model.CountryCode;
import com.alexastudillo.partyregistry.infrastructure.integration.geographic.client.GeographicReferenceClient;

import io.smallrye.mutiny.Uni;

public final class GeographicReferenceAdapter implements ActiveCountryReferenceValidationPort {

    private final GeographicReferenceClient client;

    public GeographicReferenceAdapter(GeographicReferenceClient client) {
        this.client = client;
    }

    @Override
    public Uni<CountryValidationOutcome> validateAll(
            Set<CountryCode> activeCountryCodes,
            String tenantHeaderValue,
            String auditSubject,
            String processHeaderValue) {
        return Uni.createFrom().failure(new UnsupportedOperationException("Country validation is not implemented"));
    }
}
