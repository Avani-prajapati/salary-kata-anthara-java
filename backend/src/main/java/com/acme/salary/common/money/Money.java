package com.acme.salary.common.money;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public record Money(
        @Column(name = "amount", nullable = false) BigDecimal amount,
        @Column(name = "currency", nullable = false) String currencyCode) {

    private static final Pattern ISO_CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyCode, "currencyCode");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Salary amount must be positive");
        }
        if (!ISO_CURRENCY_CODE.matcher(currencyCode).matches()) {
            throw new IllegalArgumentException("Currency must be a 3-letter ISO 4217 code");
        }
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currencyCode);
    }
}
