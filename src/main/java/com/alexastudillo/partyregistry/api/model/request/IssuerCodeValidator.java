package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.normalization.IssuerCodeNormalization;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

/** Reports independent issuer length/structure violations while retaining original values for retry identity. */
public final class IssuerCodeValidator implements ConstraintValidator<ValidIssuerCode, String> {

    @Override
    public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
        boolean validLength = value == null || value.codePointCount(0, value.length()) <= IssuerCodeNormalization.MAX_CODE_POINTS;
        boolean validStructure = validLength && IssuerCodeNormalization.hasValidStructure(value);
        if (validLength && validStructure) return true;
        context.disableDefaultConstraintViolation();
        if (!validLength) context.buildConstraintViolationWithTemplate("issuer-code-too-long").addConstraintViolation();
        else context.buildConstraintViolationWithTemplate("issuer-code-invalid").addConstraintViolation();
        return false;
    }
}
