package com.acme.salary.employee;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record CountryCode(String value) {

    public static final Set<String> SUPPORTED = Set.of(
            "US", "IN", "GB", "DE", "FR", "CA", "JP", "AU", "NL", "SG", "BR");

    public CountryCode {
        Objects.requireNonNull(value, "value");
        if (!SUPPORTED.contains(value)) {
            throw new IllegalArgumentException("Unsupported country code: " + value);
        }
    }

    public static CountryCode of(String code) {
        return new CountryCode(code.trim().toUpperCase(Locale.ROOT));
    }
}
