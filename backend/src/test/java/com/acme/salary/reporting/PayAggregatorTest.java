package com.acme.salary.reporting;

import com.acme.salary.employee.EmployeeSalaryProjection;
import com.acme.salary.reporting.dto.CurrencyAggregate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static com.acme.salary.reporting.SalaryRow.paid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class PayAggregatorTest {

    private final PayAggregator payAggregator = new PayAggregator();

    static Stream<Arguments> singleCurrencyGroups() {
        return Stream.of(
                arguments("one salary", List.of("50000.00"), "50000.00", "50000.00"),
                arguments("two salaries", List.of("70000.00", "40000.00"), "55000.00", "55000.00"),
                arguments("three salaries", List.of("90000.00", "30000.00", "45000.00"), "45000.00", "55000.00"),
                arguments("four salaries", List.of("100000.00", "10000.00", "40000.00", "20000.00"), "30000.00", "42500.00"),
                arguments("four salaries with a repeated middle", List.of("5000.00", "9000.00", "1000.00", "5000.00"), "5000.00", "5000.00"),
                arguments("two salaries whose midpoint needs rounding", List.of("100.01", "100.00"), "100.01", "100.01"),
                arguments("three salaries whose mean needs rounding", List.of("100.00", "100.01", "100.00"), "100.00", "100.00"),
                arguments("seven unsorted salaries", List.of("12000.00", "1000.00", "9000.00", "3000.00", "7000.00", "2000.00", "8000.00"), "7000.00", "6000.00"),
                arguments("six salaries with one outlier", List.of("1000000.00", "3000.00", "1000.00", "5000.00", "2000.00", "4000.00"), "3500.00", "169166.67"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("singleCurrencyGroups")
    void computes_headcount_average_and_median_for_a_single_currency_group(
            String groupDescription, List<String> amounts, String expectedMedian, String expectedAverage) {
        List<EmployeeSalaryProjection> rows = amounts.stream()
                .<EmployeeSalaryProjection>map(amount -> paid(amount, "USD"))
                .toList();

        List<CurrencyAggregate> result = payAggregator.aggregate(rows);

        assertThat(result).singleElement().satisfies(aggregate -> {
            assertThat(aggregate.currencyCode()).isEqualTo("USD");
            assertThat(aggregate.headcount()).isEqualTo(amounts.size());
            assertThat(aggregate.medianSalary()).isEqualByComparingTo(expectedMedian);
            assertThat(aggregate.averageSalary()).isEqualByComparingTo(expectedAverage);
        });
    }

    @Test
    void median_of_an_odd_sized_sorted_list_is_its_middle_element() {
        BigDecimal median = payAggregator.median(List.of(
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("35.00"), new BigDecimal("80.00"), new BigDecimal("90.00")));

        assertThat(median).isEqualByComparingTo("35.00");
    }

    @Test
    void median_of_an_even_sized_sorted_list_is_the_mean_of_its_two_middle_elements() {
        BigDecimal median = payAggregator.median(List.of(
                new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("35.00"), new BigDecimal("90.00")));

        assertThat(median).isEqualByComparingTo("27.50");
    }

    @Test
    void keeps_each_currency_in_its_own_aggregate_without_combining_amounts() {
        List<CurrencyAggregate> result = payAggregator.aggregate(List.of(
                paid("100000.00", "USD"),
                paid("9800000.00", "JPY"),
                paid("50000.00", "USD"),
                paid("80000.00", "EUR")));

        assertThat(result).extracting(CurrencyAggregate::currencyCode).containsExactly("EUR", "JPY", "USD");
        assertThat(result).extracting(CurrencyAggregate::headcount).containsExactly(1L, 1L, 2L);
        assertThat(result.get(0).averageSalary()).isEqualByComparingTo("80000.00");
        assertThat(result.get(1).medianSalary()).isEqualByComparingTo("9800000.00");
        assertThat(result.get(2).averageSalary()).isEqualByComparingTo("75000.00");
        assertThat(result.get(2).medianSalary()).isEqualByComparingTo("75000.00");
    }

    @Test
    void aggregating_no_rows_yields_no_currency_aggregates() {
        assertThat(payAggregator.aggregate(List.of())).isEmpty();
    }
}
