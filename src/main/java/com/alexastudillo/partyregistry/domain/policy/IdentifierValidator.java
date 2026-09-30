package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;

/**
 * Validates a normalized identifier deterministically without external state or I/O.
 */
public interface IdentifierValidator {

    String key();

    IdentifierRuleVersion version();

    boolean isValid(String normalizedValue);
}
