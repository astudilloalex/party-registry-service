package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.CountryEvidence;
import java.util.concurrent.CompletionStage;

public interface GeographicReferencePort {
    CompletionStage<CountryEvidence> resolveActive(String countryCode);
}
