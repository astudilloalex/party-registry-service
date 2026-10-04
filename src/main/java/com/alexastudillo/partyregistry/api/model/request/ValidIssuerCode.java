package com.alexastudillo.partyregistry.api.model.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Validates optional issuing-entity syntax and code-point bounds without modifying raw command input. */
@Documented
@Constraint(validatedBy = IssuerCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIssuerCode {
    String message() default "issuer-code-invalid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
