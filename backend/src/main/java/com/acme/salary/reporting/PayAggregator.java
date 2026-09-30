package com.acme.salary.reporting;

import com.acme.salary.employee.EmployeeSalaryProjection;
import com.acme.salary.reporting.dto.CurrencyAggregate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

public class PayAggregator {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    public List<CurrencyAggregate> aggregate(List<EmployeeSalaryProjection> rows) {
        Map<String, List<BigDecimal>> amountsByCurrency = rows.stream().collect(groupingBy(
                EmployeeSalaryProjection::getSalaryCurrencyCode,
                TreeMap::new,
                mapping(EmployeeSalaryProjection::getSalaryAmount, toList())));
        return amountsByCurrency.entrySet().stream()
                .map(currencyGroup -> aggregateOneCurrency(currencyGroup.getKey(), currencyGroup.getValue()))
                .toList();
    }

    private CurrencyAggregate aggregateOneCurrency(String currencyCode, List<BigDecimal> amounts) {
        List<BigDecimal> sortedAmounts = amounts.stream().sorted().toList();
        return new CurrencyAggregate(currencyCode, sortedAmounts.size(), average(sortedAmounts), median(sortedAmounts));
    }

    private static BigDecimal average(List<BigDecimal> amounts) {
        BigDecimal total = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(amounts.size()), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal median(List<BigDecimal> sortedAmounts) {
        int middle = sortedAmounts.size() / 2;
        if (sortedAmounts.size() % 2 == 1) {
            return sortedAmounts.get(middle).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal lowerMiddle = sortedAmounts.get(middle - 1);
        BigDecimal upperMiddle = sortedAmounts.get(middle);
        return lowerMiddle.add(upperMiddle).divide(TWO, MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
