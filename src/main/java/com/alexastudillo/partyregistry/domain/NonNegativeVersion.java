package com.alexastudillo.partyregistry.domain;

public record NonNegativeVersion(long value) {
    public NonNegativeVersion {
        if (value < 0) {
            throw new DomainViolation("Version must be non-negative");
        }
    }

    public static NonNegativeVersion of(long value) {
        return new NonNegativeVersion(value);
    }
}
