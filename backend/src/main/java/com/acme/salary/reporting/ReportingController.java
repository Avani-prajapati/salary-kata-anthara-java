package com.acme.salary.reporting;

import com.acme.salary.employee.CountryCode;
import com.acme.salary.employee.Department;
import com.acme.salary.employee.JobLevel;
import com.acme.salary.reporting.dto.BreakdownDimension;
import com.acme.salary.reporting.dto.GroupBreakdownResponse;
import com.acme.salary.reporting.dto.PayReportCriteria;
import com.acme.salary.reporting.dto.PaySummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/reports/pay")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/summary")
    public PaySummaryResponse summary(
            @RequestParam Optional<Department> department,
            @RequestParam Optional<String> countryCode,
            @RequestParam Optional<JobLevel> jobLevel) {
        return reportingService.summarize(criteriaFrom(department, countryCode, jobLevel));
    }

    @GetMapping("/breakdown")
    public List<GroupBreakdownResponse> breakdown(
            @RequestParam String dimension,
            @RequestParam Optional<Department> department,
            @RequestParam Optional<String> countryCode,
            @RequestParam Optional<JobLevel> jobLevel) {
        return reportingService.breakdownBy(
                BreakdownDimension.fromQueryValue(dimension),
                criteriaFrom(department, countryCode, jobLevel));
    }

    private PayReportCriteria criteriaFrom(
            Optional<Department> department, Optional<String> countryCode, Optional<JobLevel> jobLevel) {
        return new PayReportCriteria(department, countryCode.map(CountryCode::of).map(CountryCode::value), jobLevel);
    }
}
