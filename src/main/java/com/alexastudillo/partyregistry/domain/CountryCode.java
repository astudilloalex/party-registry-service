package com.alexastudillo.partyregistry.domain;

import java.util.regex.Pattern;

public record CountryCode(String value) {
    private static final Pattern FORMAT = Pattern.compile("[A-Z]{2}");

    public CountryCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new DomainViolation("Country code must contain exactly two uppercase ASCII letters");
        }
    }

    public static CountryCode of(String value) {
        return new CountryCode(value);
    }
}
