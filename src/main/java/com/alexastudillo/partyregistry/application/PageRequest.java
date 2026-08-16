package com.alexastudillo.partyregistry.application;

public record PageRequest(int page, int size) {
    public PageRequest {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApplicationFailure("VALIDATION_ERROR", "Page must be non-negative and size must be between 1 and 100");
        }
    }

    public static PageRequest defaults() {
        return new PageRequest(0, 20);
    }
}
