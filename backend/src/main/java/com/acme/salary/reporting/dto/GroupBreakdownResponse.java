package com.acme.salary.reporting.dto;

import java.util.List;

public record GroupBreakdownResponse(String groupKey, List<CurrencyAggregate> byCurrency) {
}
