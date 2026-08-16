package com.alexastudillo.partyregistry.domain;

public record PositiveVersion(int value) {
    public PositiveVersion {
        if (value <= 0) {
            throw new DomainViolation("Version must be positive");
        }
    }

    public static PositiveVersion of(int value) {
        return new PositiveVersion(value);
    }
}
