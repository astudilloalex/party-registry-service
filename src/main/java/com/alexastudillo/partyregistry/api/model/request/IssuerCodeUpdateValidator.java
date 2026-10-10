package com.alexastudillo.partyregistry.api.model.request;

import com.alexastudillo.partyregistry.domain.model.FieldUpdate;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

/** Applies the existing raw issuer constraint only to supplied metadata, preserving omitted historical values. */
public final class IssuerCodeUpdateValidator implements ConstraintValidator<ValidIssuerCode, FieldUpdate<?>> {
    private final IssuerCodeValidator strings = new IssuerCodeValidator();

    @Override
    public boolean isValid(@Nullable FieldUpdate<?> update, ConstraintValidatorContext context) {
        if (update == null || !update.isPresent()) { return true; }
        var value = update.value();
        return value == null || value instanceof String text && strings.isValid(text, context);
    }
}
