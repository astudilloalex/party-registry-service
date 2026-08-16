package com.alexastudillo.partyregistry.application;

import java.util.Objects;

public final class ApplicationFailure extends RuntimeException {
    private final String code;

    public ApplicationFailure(String code, String message) {
        super(requireMessage(message));
        this.code = Objects.requireNonNull(code, "code");
    }

    public String code() {
        return code;
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Application failure message must not be blank");
        }
        return message;
    }
}
