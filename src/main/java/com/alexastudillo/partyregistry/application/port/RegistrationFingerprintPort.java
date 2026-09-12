package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.command.PartyRegistrationCommand;

/**
 * Produces and compares keyed fingerprints of effective Party registration inputs.
 */
public interface RegistrationFingerprintPort {

    /**
     * Produces a deterministic keyed fingerprint without retaining canonical input.
     *
     * @param command effective registration command
     * @return lowercase hexadecimal HMAC-SHA-256 fingerprint
     */
    String fingerprint(PartyRegistrationCommand command);

    /**
     * Compares two encoded fingerprints in constant time.
     *
     * @param expectedFingerprint expected encoded fingerprint
     * @param actualFingerprint actual encoded fingerprint
     * @return {@code true} only when both fingerprints are equal
     */
    boolean matches(String expectedFingerprint, String actualFingerprint);
}
