package com.alexastudillo.partyregistry.domain;

public final class DisplayName {
    private DisplayName() {}

    public static String forNaturalPerson(String givenNames, String familyNames) {
        return normalize(givenNames) + " " + normalize(familyNames);
    }

    public static String forLegalEntity(String legalName) {
        return normalize(legalName);
    }

    private static String normalize(String value) {
        if (value == null) {
            throw new DomainViolation("Display-name source must not be null");
        }
        StringBuilder normalized = new StringBuilder();
        boolean pendingSpace = false;
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = normalized.length() > 0;
            } else {
                if (pendingSpace) {
                    normalized.append(' ');
                    pendingSpace = false;
                }
                normalized.appendCodePoint(codePoint);
            }
        }
        if (normalized.isEmpty()) {
            throw new DomainViolation("Display-name source must not be blank");
        }
        return normalized.toString();
    }
}
