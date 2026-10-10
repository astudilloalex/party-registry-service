package com.alexastudillo.partyregistry.application.model;

/** Defines independent versioned replay namespaces for the five keyed identifier operations. */
public enum PartyIdentifierOperation {
    CREATE("party.identifier.create.v1"),
    VERIFY("party.identifier.verify.v1"),
    REJECT("party.identifier.reject.v1"),
    REVOKE("party.identifier.revoke.v1"),
    SET_PRIMARY("party.identifier.set-primary.v1");

    private final String namespace;

    PartyIdentifierOperation(String namespace) {
        this.namespace = namespace;
    }

    public String namespace() {
        return namespace;
    }
}
