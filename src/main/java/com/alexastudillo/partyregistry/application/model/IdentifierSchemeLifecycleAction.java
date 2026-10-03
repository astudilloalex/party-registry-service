package com.alexastudillo.partyregistry.application.model;

/** Selects one catalog transition and its independent durable completion namespace. */
public enum IdentifierSchemeLifecycleAction {
    ACTIVATE("identifier-scheme.activate.v1"),
    DEPRECATE("identifier-scheme.deprecate.v1"),
    RETIRE("identifier-scheme.retire.v1");

    private final String operation;

    IdentifierSchemeLifecycleAction(String operation) {
        this.operation = operation;
    }

    public String operation() {
        return operation;
    }
}
