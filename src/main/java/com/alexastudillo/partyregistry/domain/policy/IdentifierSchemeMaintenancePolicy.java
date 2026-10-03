package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeChanges;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;

import java.time.Instant;
import java.util.Objects;

/** Admits resulting draft processing configuration while retaining historical rules during descriptive operational maintenance. */
public final class IdentifierSchemeMaintenancePolicy {
    private final IdentifierRuleCatalog rules;

    public IdentifierSchemeMaintenancePolicy(IdentifierRuleCatalog rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /** Applies state, exhaustion, and candidate invariants before draft rule admission without mutating the original. */
    public IdentifierScheme apply(IdentifierScheme current, IdentifierSchemeChanges changes, Instant occurredAt, String actor) {
        Objects.requireNonNull(current, "current");
        var candidate = current.applyChanges(changes, occurredAt, actor);
        if (current.status() == IdentifierSchemeStatus.DRAFT) {
            rules.requireSupportedKeys(candidate.normalizerKey(), candidate.validatorKey());
        }
        return candidate;
    }
}
