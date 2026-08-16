package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.ApplicationFailure;
import java.util.Objects;

final class UseCaseSupport {
    private UseCaseSupport() {}

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new ApplicationFailure("VALIDATION_ERROR", field + " is required");
        }
        return value;
    }

    static long expectedVersion(Long value) {
        if (value == null) {
            throw new ApplicationFailure("PRECONDITION_REQUIRED", "If-Match is required");
        }
        if (value < 0) {
            throw new ApplicationFailure("VALIDATION_ERROR", "If-Match version must be non-negative");
        }
        return value;
    }

    static <T> T found(T value, String resource) {
        if (value == null) {
            throw new ApplicationFailure("NOT_FOUND", resource + " was not found");
        }
        return value;
    }

    static String nonblank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApplicationFailure("VALIDATION_ERROR", field + " must not be blank");
        }
        return value;
    }
}
