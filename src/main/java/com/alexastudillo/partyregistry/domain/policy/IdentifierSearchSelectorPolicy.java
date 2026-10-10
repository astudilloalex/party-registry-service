package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;

import java.util.Objects;

/** Selects existing versioned normalization independently of scheme lifecycle and new-registration eligibility. */
public final class IdentifierSearchSelectorPolicy {
    private final IdentifierRuleCatalog catalog;

    public IdentifierSearchSelectorPolicy(IdentifierRuleCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /** Preserves normalization expansion without imposing registration bounds or validating a complete checksum. */
    public IdentifierSearchSelector select(IdentifierScheme scheme, String rawValue) {
        Objects.requireNonNull(scheme, "scheme");
        IdentifierNormalizer normalizer = catalog.resolveNormalizer(scheme.normalizerKey());
        return new IdentifierSearchSelector(normalizer, normalizer.normalize(rawValue));
    }
}
