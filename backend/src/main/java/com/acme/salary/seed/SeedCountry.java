package com.acme.salary.seed;

import com.acme.salary.employee.CountryCode;

enum SeedCountry {
    US("USD", 30),
    IN("INR", 15),
    GB("GBP", 10),
    DE("EUR", 10),
    CA("CAD", 7),
    JP("JPY", 6),
    FR("EUR", 5),
    AU("AUD", 5),
    NL("EUR", 4),
    SG("SGD", 4),
    BR("BRL", 4);

    private final String currencyCode;
    private final int weight;

    SeedCountry(String currencyCode, int weight) {
        this.currencyCode = currencyCode;
        this.weight = weight;
    }

    CountryCode countryCode() {
        return CountryCode.of(name());
    }

    String currencyCode() {
        return currencyCode;
    }

    int weight() {
        return weight;
    }
}
