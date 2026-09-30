package com.acme.salary.compensation.dto;

import com.acme.salary.compensation.SalaryHistory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SalaryHistoryEntryResponse(
        BigDecimal amount,
        String currencyCode,
        LocalDate effectiveDate,
        Instant recordedAt) {

    public static SalaryHistoryEntryResponse from(SalaryHistory entry) {
        return new SalaryHistoryEntryResponse(
                entry.getAmount(), entry.getCurrencyCode(), entry.getEffectiveDate(), entry.getRecordedAt());
    }
}
