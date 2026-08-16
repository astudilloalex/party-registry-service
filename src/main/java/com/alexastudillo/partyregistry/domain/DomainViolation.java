package com.alexastudillo.partyregistry.domain;

public final class DomainViolation extends IllegalArgumentException {
    public DomainViolation(String message) {
        super(message);
    }
}
