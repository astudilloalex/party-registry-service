package com.alexastudillo.partyregistry.api.support;

import com.alexastudillo.api.response.application.ApiResponseException;
import com.alexastudillo.partyregistry.api.error.PartyResponseCode;
import com.alexastudillo.partyregistry.api.model.request.NationalityCreateRequest;
import com.alexastudillo.partyregistry.api.model.request.NationalityPatchRequest;
import com.alexastudillo.partyregistry.domain.model.NationalityPeriod;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Locale;

/** Validates nationality-only body semantics after strict JSON shape binding. */
@ApplicationScoped
public class NationalityRequestParameters {

    /** Normalizes only ASCII alpha-2 codes after stripping surrounding Java whitespace. */
    public String countryCode(NationalityCreateRequest request) {
        if (request == null) {
            throw new ApiResponseException(PartyResponseCode.REQUEST_BODY_REQUIRED);
        }
        String countryCode = request.countryCode();
        if (countryCode == null) {
            throw new ApiResponseException(PartyResponseCode.COUNTRY_CODE_REQUIRED);
        }
        String stripped = countryCode.strip();
        if (!stripped.matches("[a-zA-Z]{2}")) {
            throw new ApiResponseException(PartyResponseCode.COUNTRY_CODE_INVALID);
        }
        return stripped.toUpperCase(Locale.ROOT);
    }

    /** Requires a nonempty PATCH while preserving explicit null bound updates. */
    public NationalityPatchRequest patch(NationalityPatchRequest request) {
        if (request == null) {
            throw new ApiResponseException(PartyResponseCode.REQUEST_BODY_REQUIRED);
        }
        if (request.empty()) {
            throw new ApiResponseException(PartyResponseCode.PATCH_PROPERTY_REQUIRED);
        }
        return request;
    }

    /** Preserves open bounds; chronological invariants are checked by Domain. */
    public NationalityPeriod period(NationalityCreateRequest request) {
        return new NationalityPeriod(request.validFrom(), request.validUntil());
    }
}
