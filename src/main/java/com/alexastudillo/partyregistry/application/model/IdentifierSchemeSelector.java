package com.alexastudillo.partyregistry.application.model;

import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeId;

import java.util.Objects;

/** Selects a global scheme by typed identity or exact case-sensitive catalog code. */
public sealed interface IdentifierSchemeSelector {

    /** Selects one global scheme identity. */
    record ById(IdentifierSchemeId id) implements IdentifierSchemeSelector {
        public ById {
            Objects.requireNonNull(id, "id");
        }
    }

    /** Preserves an exact, bounded catalog code without normalization. */
    record ByCode(String code) implements IdentifierSchemeSelector {
        public ByCode {
            Objects.requireNonNull(code, "code");
            if (code.isBlank() || code.codePointCount(0, code.length()) > 64) {
                throw new IllegalArgumentException("Identifier scheme selector code is outside the supported range");
            }
        }
    }
}
