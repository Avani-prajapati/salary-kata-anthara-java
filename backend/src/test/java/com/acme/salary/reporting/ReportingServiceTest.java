package com.acme.salary.reporting;

import com.acme.salary.employee.Department;
import com.acme.salary.employee.EmployeeService;
import com.acme.salary.employee.JobLevel;
import com.acme.salary.reporting.dto.BreakdownDimension;
import com.acme.salary.reporting.dto.CurrencyAggregate;
import com.acme.salary.reporting.dto.GroupBreakdownResponse;
import com.acme.salary.reporting.dto.PayReportCriteria;
import com.acme.salary.reporting.dto.PaySummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.acme.salary.reporting.SalaryRow.paid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportingServiceTest {

    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final ReportingService reportingService = new ReportingService(employeeService, new PayAggregator());

    @BeforeEach
    void anOrgPaidInThreeCurrencies() {
        when(employeeService.findAllForReporting()).thenReturn(List.of(
                paid(Department.ENGINEERING, "US", JobLevel.SENIOR, "150000.00", "USD"),
                paid(Department.ENGINEERING, "US", JobLevel.MID, "110000.00", "USD"),
                paid(Department.ENGINEERING, "DE", JobLevel.SENIOR, "95000.00", "EUR"),
                paid(Department.SALES, "JP", JobLevel.MID, "6200000.00", "JPY"),
                paid(Department.SALES, "US", JobLevel.INTERN, "40000.00", "USD")));
    }

    @Test
    void summary_reports_total_headcount_and_one_aggregate_per_currency() {
        PaySummaryResponse summary = reportingService.summarize(PayReportCriteria.unfiltered());

        assertThat(summary.totalHeadcount()).isEqualTo(5);
        assertThat(summary.byCurrency()).extracting(CurrencyAggregate::currencyCode).containsExactly("EUR", "JPY", "USD");
        CurrencyAggregate usd = summary.byCurrency().get(2);
        assertThat(usd.headcount()).isEqualTo(3);
        assertThat(usd.averageSalary()).isEqualByComparingTo("100000.00");
        assertThat(usd.medianSalary()).isEqualByComparingTo("110000.00");
    }

    @Test
    void summary_never_folds_one_currency_into_another() {
        PaySummaryResponse summary = reportingService.summarize(PayReportCriteria.unfiltered());

        CurrencyAggregate jpy = summary.byCurrency().get(1);
        assertThat(jpy.headcount()).isEqualTo(1);
        assertThat(jpy.averageSalary()).isEqualByComparingTo("6200000.00");
        assertThat(jpy.medianSalary()).isEqualByComparingTo("6200000.00");
    }

    @Test
    void department_breakdown_is_keyed_by_department_and_split_by_currency_within_each_group() {
        List<GroupBreakdownResponse> breakdown = reportingService.breakdownBy(BreakdownDimension.DEPARTMENT, PayReportCriteria.unfiltered());

        assertThat(breakdown).extracting(GroupBreakdownResponse::groupKey).containsExactly("ENGINEERING", "SALES");
        List<CurrencyAggregate> engineering = breakdown.get(0).byCurrency();
        assertThat(engineering).extracting(CurrencyAggregate::currencyCode).containsExactly("EUR", "USD");
        assertThat(engineering).extracting(CurrencyAggregate::headcount).containsExactly(1L, 2L);
        assertThat(engineering.get(0).averageSalary()).isEqualByComparingTo("95000.00");
        assertThat(engineering.get(1).averageSalary()).isEqualByComparingTo("130000.00");
        assertThat(breakdown.get(1).byCurrency()).extracting(CurrencyAggregate::currencyCode).containsExactly("JPY", "USD");
    }

    @Test
    void country_breakdown_is_keyed_by_country_code() {
        List<GroupBreakdownResponse> breakdown = reportingService.breakdownBy(BreakdownDimension.COUNTRY, PayReportCriteria.unfiltered());

        assertThat(breakdown).extracting(GroupBreakdownResponse::groupKey).containsExactly("DE", "JP", "US");
        CurrencyAggregate us = breakdown.get(2).byCurrency().getFirst();
        assertThat(us.currencyCode()).isEqualTo("USD");
        assertThat(us.headcount()).isEqualTo(3);
        assertThat(us.medianSalary()).isEqualByComparingTo("110000.00");
    }

    @Test
    void job_level_breakdown_is_keyed_by_job_level_in_seniority_order() {
        List<GroupBreakdownResponse> breakdown = reportingService.breakdownBy(BreakdownDimension.JOB_LEVEL, PayReportCriteria.unfiltered());

        assertThat(breakdown).extracting(GroupBreakdownResponse::groupKey).containsExactly("INTERN", "MID", "SENIOR");
        assertThat(breakdown.get(1).byCurrency()).extracting(CurrencyAggregate::currencyCode).containsExactly("JPY", "USD");
        assertThat(breakdown.get(2).byCurrency()).extracting(CurrencyAggregate::currencyCode).containsExactly("EUR", "USD");
    }

    @Test
    void a_department_filter_narrows_the_summary_to_that_department_only() {
        PayReportCriteria engineeringOnly = new PayReportCriteria(
                Optional.of(Department.ENGINEERING), Optional.empty(), Optional.empty());

        PaySummaryResponse summary = reportingService.summarize(engineeringOnly);

        assertThat(summary.totalHeadcount()).isEqualTo(3);
        assertThat(summary.byCurrency()).extracting(CurrencyAggregate::currencyCode).containsExactly("EUR", "USD");
        assertThat(summary.byCurrency().get(1).headcount()).isEqualTo(2);
        assertThat(summary.byCurrency().get(1).averageSalary()).isEqualByComparingTo("130000.00");
    }

    @Test
    void a_department_filter_narrows_a_breakdown_by_another_dimension_to_that_department_only() {
        PayReportCriteria salesOnly = new PayReportCriteria(
                Optional.of(Department.SALES), Optional.empty(), Optional.empty());

        List<GroupBreakdownResponse> breakdown = reportingService.breakdownBy(BreakdownDimension.COUNTRY, salesOnly);

        assertThat(breakdown).extracting(GroupBreakdownResponse::groupKey).containsExactly("JP", "US");
        assertThat(breakdown.get(0).byCurrency()).extracting(CurrencyAggregate::currencyCode).containsExactly("JPY");
        assertThat(breakdown.get(1).byCurrency()).extracting(CurrencyAggregate::currencyCode).containsExactly("USD");
    }

    @Test
    void a_filter_combination_matching_nobody_reports_zero_headcount_with_no_currencies_rather_than_an_error() {
        PayReportCriteria matchesNobody = new PayReportCriteria(
                Optional.of(Department.LEGAL), Optional.empty(), Optional.empty());

        PaySummaryResponse summary = reportingService.summarize(matchesNobody);

        assertThat(summary.totalHeadcount()).isZero();
        assertThat(summary.byCurrency()).isEmpty();
    }
}
