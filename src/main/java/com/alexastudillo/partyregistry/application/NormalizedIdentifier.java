package com.alexastudillo.partyregistry.application;

public record NormalizedIdentifier(String value, int normalizationVersion) {
    public NormalizedIdentifier {
        if (value == null || value.isBlank() || normalizationVersion < 1) {
            throw new ApplicationFailure("VALIDATION_ERROR", "Identifier normalization produced an invalid result");
        }
    }
}
