package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.error.DomainValidationException;
import com.alexastudillo.partyregistry.domain.error.DomainViolation;
import com.alexastudillo.partyregistry.domain.model.IdentifierScheme;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeLengthBounds;
import com.alexastudillo.partyregistry.domain.model.IdentifierSchemeStatus;

import java.time.Instant;
import java.util.Objects;

/** Admits only usable draft scheme configurations and produces one immutable active revision. */
public final class IdentifierSchemeActivationPolicy {

    private final IdentifierRuleCatalog rules;

    public IdentifierSchemeActivationPolicy(IdentifierRuleCatalog rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /** Checks state, exhaustion, and eligibility in order without interpreting HTTP or requiring sample values. */
    public IdentifierScheme activate(IdentifierScheme current, Instant occurredAt, String userId) {
        Objects.requireNonNull(current, "current");
        if (current.status() != IdentifierSchemeStatus.DRAFT) {
            throw new DomainValidationException(DomainViolation.IDENTIFIER_SCHEME_ACTIVATION_INVALID_STATE,
                    "Only draft identifier schemes can be activated");
        }
        var nextVersion = current.version().next();
        var bounds = new IdentifierSchemeLengthBounds(current.minimumLength(), current.maximumLength());
        rules.requireSupportedKeys(current.normalizerKey(), current.validatorKey());
        return new IdentifierScheme(current.id(), current.code(), current.issuingCountryCode(), current.category(),
                current.applicableSubjectType(), current.name(), current.description(), current.normalizerKey(),
                current.validatorKey(), bounds.minimumLength(), bounds.maximumLength(), current.requiresExpiration(),
                IdentifierSchemeStatus.ACTIVE, nextVersion, current.auditInfo().updated(occurredAt, userId));
    }
}
