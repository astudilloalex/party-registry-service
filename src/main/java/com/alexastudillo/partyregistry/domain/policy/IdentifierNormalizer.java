package com.alexastudillo.partyregistry.domain.policy;

import com.alexastudillo.partyregistry.domain.model.IdentifierRuleVersion;

/**
 * Normalizes a complete identifier deterministically without external state or I/O.
 */
public interface IdentifierNormalizer {

    String key();

    IdentifierRuleVersion version();

    String normalize(String completeValue);
}
