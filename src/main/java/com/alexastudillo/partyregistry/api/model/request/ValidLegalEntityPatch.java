package com.alexastudillo.partyregistry.api.model.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Applies presence-aware legal PATCH rules using the public JSON property paths. */
@Documented
@Constraint(validatedBy = LegalEntityPatchValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidLegalEntityPatch {
    String message() default "patch-property-required";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
