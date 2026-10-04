package com.alexastudillo.partyregistry.application.command;

import org.jspecify.annotations.Nullable;

/** Guards optional action-key shape while retaining its exact submitted representation. */
final class PartyIdentifierCommandKeys {

    private PartyIdentifierCommandKeys() {
    }

    static void validateOptional(@Nullable String key) {
        if (key != null && (key.isBlank() || key.codePointCount(0, key.length()) > 128)) {
            throw new IllegalArgumentException("Identifier action key is outside its accepted shape bound");
        }
    }
}
