package com.acme.salary.reporting;

import com.acme.salary.employee.Department;
import com.acme.salary.employee.JobLevel;
import com.acme.salary.reporting.dto.BreakdownDimension;
import com.acme.salary.reporting.dto.CurrencyAggregate;
import com.acme.salary.reporting.dto.GroupBreakdownResponse;
import com.acme.salary.reporting.dto.PayReportCriteria;
import com.acme.salary.reporting.dto.PaySummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportingController.class)
class ReportingControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportingService reportingService;

    @Test
    void summary_returns_total_headcount_and_per_currency_aggregates() throws Exception {
        when(reportingService.summarize(PayReportCriteria.unfiltered())).thenReturn(new PaySummaryResponse(3, List.of(
                new CurrencyAggregate("EUR", 1, new BigDecimal("82000.00"), new BigDecimal("82000.00")),
                new CurrencyAggregate("USD", 2, new BigDecimal("75000.00"), new BigDecimal("75000.00")))));

        mockMvc.perform(get("/api/reports/pay/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHeadcount").value(3))
                .andExpect(jsonPath("$.byCurrency", hasSize(2)))
                .andExpect(jsonPath("$.byCurrency[0].currencyCode").value("EUR"))
                .andExpect(jsonPath("$.byCurrency[0].headcount").value(1))
                .andExpect(jsonPath("$.byCurrency[0].averageSalary").value(82000.00))
                .andExpect(jsonPath("$.byCurrency[0].medianSalary").value(82000.00))
                .andExpect(jsonPath("$.byCurrency[1].currencyCode").value("USD"))
                .andExpect(jsonPath("$.byCurrency[1].headcount").value(2));
    }

    @Test
    void breakdown_by_job_level_returns_groups_with_per_currency_aggregates() throws Exception {
        when(reportingService.breakdownBy(BreakdownDimension.JOB_LEVEL, PayReportCriteria.unfiltered())).thenReturn(List.of(
                new GroupBreakdownResponse("SENIOR", List.of(
                        new CurrencyAggregate("USD", 4, new BigDecimal("140000.00"), new BigDecimal("137500.00"))))));

        mockMvc.perform(get("/api/reports/pay/breakdown").param("dimension", "jobLevel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].groupKey").value("SENIOR"))
                .andExpect(jsonPath("$[0].byCurrency[0].currencyCode").value("USD"))
                .andExpect(jsonPath("$[0].byCurrency[0].headcount").value(4))
                .andExpect(jsonPath("$[0].byCurrency[0].averageSalary").value(140000.00))
                .andExpect(jsonPath("$[0].byCurrency[0].medianSalary").value(137500.00));
    }

    @Test
    void breakdown_by_department_and_by_country_bind_their_dimensions() throws Exception {
        when(reportingService.breakdownBy(BreakdownDimension.DEPARTMENT, PayReportCriteria.unfiltered()))
                .thenReturn(List.of(new GroupBreakdownResponse("ENGINEERING", List.of())));
        when(reportingService.breakdownBy(BreakdownDimension.COUNTRY, PayReportCriteria.unfiltered()))
                .thenReturn(List.of(new GroupBreakdownResponse("DE", List.of())));

        mockMvc.perform(get("/api/reports/pay/breakdown").param("dimension", "department"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupKey").value("ENGINEERING"));
        mockMvc.perform(get("/api/reports/pay/breakdown").param("dimension", "country"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupKey").value("DE"));
    }

    @Test
    void an_unknown_breakdown_dimension_is_rejected_with_a_problem_detail() throws Exception {
        mockMvc.perform(get("/api/reports/pay/breakdown").param("dimension", "salary"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void summary_binds_a_single_department_filter_into_the_criteria() throws Exception {
        PayReportCriteria departmentOnly = new PayReportCriteria(
                Optional.of(Department.ENGINEERING), Optional.empty(), Optional.empty());
        when(reportingService.summarize(departmentOnly)).thenReturn(new PaySummaryResponse(2, List.of()));

        mockMvc.perform(get("/api/reports/pay/summary").param("department", "ENGINEERING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHeadcount").value(2));
    }

    @Test
    void summary_binds_a_single_country_filter_into_the_criteria_uppercased() throws Exception {
        PayReportCriteria countryOnly = new PayReportCriteria(
                Optional.empty(), Optional.of("DE"), Optional.empty());
        when(reportingService.summarize(countryOnly)).thenReturn(new PaySummaryResponse(1, List.of()));

        mockMvc.perform(get("/api/reports/pay/summary").param("countryCode", "de"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHeadcount").value(1));
    }

    @Test
    void summary_binds_department_country_and_job_level_filters_in_combination() throws Exception {
        PayReportCriteria combined = new PayReportCriteria(
                Optional.of(Department.ENGINEERING), Optional.of("DE"), Optional.of(JobLevel.SENIOR));
        when(reportingService.summarize(combined)).thenReturn(new PaySummaryResponse(1, List.of()));

        mockMvc.perform(get("/api/reports/pay/summary")
                        .param("department", "ENGINEERING")
                        .param("countryCode", "DE")
                        .param("jobLevel", "SENIOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHeadcount").value(1));
    }

    @Test
    void breakdown_binds_the_same_filters_alongside_its_dimension() throws Exception {
        PayReportCriteria jobLevelOnly = new PayReportCriteria(
                Optional.empty(), Optional.empty(), Optional.of(JobLevel.SENIOR));
        when(reportingService.breakdownBy(BreakdownDimension.COUNTRY, jobLevelOnly))
                .thenReturn(List.of(new GroupBreakdownResponse("DE", List.of())));

        mockMvc.perform(get("/api/reports/pay/breakdown")
                        .param("dimension", "country")
                        .param("jobLevel", "SENIOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupKey").value("DE"));
    }
}
