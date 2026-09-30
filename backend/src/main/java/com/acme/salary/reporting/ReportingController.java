package com.acme.salary.reporting;

import com.acme.salary.reporting.dto.BreakdownDimension;
import com.acme.salary.reporting.dto.GroupBreakdownResponse;
import com.acme.salary.reporting.dto.PayReportCriteria;
import com.acme.salary.reporting.dto.PaySummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports/pay")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/summary")
    public PaySummaryResponse summary() {
        return reportingService.summarize(PayReportCriteria.unfiltered());
    }

    @GetMapping("/breakdown")
    public List<GroupBreakdownResponse> breakdown(@RequestParam String dimension) {
        return reportingService.breakdownBy(BreakdownDimension.fromQueryValue(dimension), PayReportCriteria.unfiltered());
    }
}
