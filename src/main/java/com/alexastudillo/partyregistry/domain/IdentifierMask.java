package com.alexastudillo.partyregistry.domain;

public final class IdentifierMask {
    private IdentifierMask() {}

    public static String mask(String normalizedIdentifier) {
        if (normalizedIdentifier == null) {
            throw new DomainViolation("Normalized identifier must not be null");
        }
        if (normalizedIdentifier.length() <= 4) {
            return "****";
        }
        return "****" + normalizedIdentifier.substring(normalizedIdentifier.length() - 4);
    }
}
