package com.alexastudillo.partyregistry.domain.model;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;

import java.util.UUID;

/** Identifies one Party nationality independently of its validity period. */
public record NationalityId(UUID value) {

    public NationalityId {
        if (value == null) {
            throw new DomainValidationException(DomainViolation.NATIONALITY_ID_REQUIRED,
                    "Nationality identifier is required");
        }
    }
}
