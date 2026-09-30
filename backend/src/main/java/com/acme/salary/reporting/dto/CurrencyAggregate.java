package com.acme.salary.reporting.dto;

import java.math.BigDecimal;

public record CurrencyAggregate(
        String currencyCode,
        long headcount,
        BigDecimal averageSalary,
        BigDecimal medianSalary) {
}
