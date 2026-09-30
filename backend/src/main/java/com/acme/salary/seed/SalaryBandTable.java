package com.acme.salary.seed;

import com.acme.salary.employee.JobLevel;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

final class SalaryBandTable {

    record SalaryRange(long minimum, long maximum) {
    }

    private static final Map<JobLevel, SalaryRange> USD_BANDS = new EnumMap<>(Map.of(
            JobLevel.INTERN, new SalaryRange(25_000, 40_000),
            JobLevel.ENTRY, new SalaryRange(50_000, 75_000),
            JobLevel.MID, new SalaryRange(75_000, 110_000),
            JobLevel.SENIOR, new SalaryRange(110_000, 150_000),
            JobLevel.LEAD, new SalaryRange(140_000, 185_000),
            JobLevel.MANAGER, new SalaryRange(150_000, 200_000),
            JobLevel.DIRECTOR, new SalaryRange(190_000, 260_000),
            JobLevel.VP, new SalaryRange(250_000, 350_000),
            JobLevel.EXECUTIVE, new SalaryRange(350_000, 550_000)));

    private static final Map<String, BigDecimal> LOCAL_MARKET_SCALE_FROM_USD = Map.of(
            "USD", new BigDecimal("1"),
            "INR", new BigDecimal("25"),
            "GBP", new BigDecimal("0.7"),
            "EUR", new BigDecimal("0.75"),
            "CAD", new BigDecimal("1.1"),
            "JPY", new BigDecimal("110"),
            "AUD", new BigDecimal("1.3"),
            "SGD", new BigDecimal("1.1"),
            "BRL", new BigDecimal("2.5"));

    private SalaryBandTable() {
    }

    static SalaryRange rangeFor(JobLevel jobLevel, String currencyCode) {
        BigDecimal scale = Optional.ofNullable(LOCAL_MARKET_SCALE_FROM_USD.get(currencyCode))
                .orElseThrow(() -> new IllegalArgumentException("No salary band for currency " + currencyCode));
        SalaryRange usdRange = USD_BANDS.get(jobLevel);
        return new SalaryRange(scaled(usdRange.minimum(), scale), scaled(usdRange.maximum(), scale));
    }

    private static long scaled(long usdAmount, BigDecimal scale) {
        return BigDecimal.valueOf(usdAmount).multiply(scale).longValueExact();
    }
}
