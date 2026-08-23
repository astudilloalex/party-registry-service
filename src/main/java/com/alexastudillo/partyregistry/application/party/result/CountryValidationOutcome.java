package com.alexastudillo.partyregistry.application.party.result;

import com.alexastudillo.partyregistry.domain.party.model.CountryCode;
import java.util.Map;

public sealed interface CountryValidationOutcome
        permits CountryValidationOutcome.AllActive,
                CountryValidationOutcome.InvalidReferences,
                CountryValidationOutcome.ValidationUnavailable {

    record AllActive() implements CountryValidationOutcome {
    }

    record InvalidReferences(Map<CountryCode, InvalidReferenceReason> invalidReferences)
            implements CountryValidationOutcome {
    }

    record ValidationUnavailable() implements CountryValidationOutcome {
    }

    enum InvalidReferenceReason {
        UNKNOWN,
        INACTIVE
    }
}
