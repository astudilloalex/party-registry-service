package com.alexastudillo.partyregistry.domain;

import java.time.LocalDate;

public final class LifeDatePolicy {
    private LifeDatePolicy() {}

    public static void validate(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new DomainViolation("Lifecycle end date must not precede its start date");
        }
    }
}
