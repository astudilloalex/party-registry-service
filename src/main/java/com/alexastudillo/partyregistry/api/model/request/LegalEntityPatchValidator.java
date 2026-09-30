package com.alexastudillo.partyregistry.api.model.request;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Reports missing PATCH content and mandatory nulls at deterministic JSON field paths. */
public final class LegalEntityPatchValidator implements ConstraintValidator<ValidLegalEntityPatch, LegalEntityPatchRequest> {

    @Override
    public boolean isValid(LegalEntityPatchRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        boolean valid = true;
        if (request.empty()) {
            context.buildConstraintViolationWithTemplate("patch-property-required").addConstraintViolation();
            valid = false;
        }
        String legalName = request.legalName();
        if (request.legalNamePresent() && (legalName == null || legalName.isBlank())) {
            context.buildConstraintViolationWithTemplate("legal-name-required")
                    .addPropertyNode("legalName").addConstraintViolation();
            valid = false;
        }
        if (request.incorporationCountryCodePresent() && request.incorporationCountryCode() == null) {
            context.buildConstraintViolationWithTemplate("incorporation-country-code-required")
                    .addPropertyNode("incorporationCountryCode").addConstraintViolation();
            valid = false;
        }
        return valid;
    }
}
