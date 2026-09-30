package com.acme.salary.reporting.dto;

import java.util.List;

public record PaySummaryResponse(long totalHeadcount, List<CurrencyAggregate> byCurrency) {
}
