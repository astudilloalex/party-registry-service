package com.alexastudillo.partyregistry.architecture.fixture.isolation.domain;

import java.security.Provider;

import javax.crypto.Cipher;

/**
 * Represents forbidden Domain dependencies on Java cryptographic providers.
 */
public final class DomainDependingOnCryptography {

    public String providerName(Provider provider) {
        return provider.getName();
    }

    public String cipherAlgorithm(Cipher cipher) {
        return cipher.getAlgorithm();
    }
}
