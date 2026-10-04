package com.alexastudillo.partyregistry.application.model;

import org.jspecify.annotations.Nullable;

/** Retains exact optional withdrawal text for fingerprinting and private audit without logging its contents. */
public record PartyIdentifierReason(@Nullable String reasonCode, @Nullable String reasonDetail) {

    public PartyIdentifierReason {
        requireBound(reasonCode, 64);
        requireBound(reasonDetail, 1000);
    }

    /** Represents absent body/properties without confusing absence with supplied empty strings. */
    public static PartyIdentifierReason none() {
        return new PartyIdentifierReason(null, null);
    }

    public boolean isAbsent() {
        return reasonCode == null && reasonDetail == null;
    }

    @Override
    public String toString() {
        return "PartyIdentifierReason[redacted]";
    }

    private static void requireBound(@Nullable String value, int maximum) {
        if (value != null && value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException("Identifier reason exceeds its code-point bound");
        }
    }
}
