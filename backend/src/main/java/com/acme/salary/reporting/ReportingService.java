package com.acme.salary.reporting;

import com.acme.salary.employee.EmployeeSalaryProjection;
import com.acme.salary.employee.EmployeeService;
import com.acme.salary.reporting.dto.BreakdownDimension;
import com.acme.salary.reporting.dto.CurrencyAggregate;
import com.acme.salary.reporting.dto.GroupBreakdownResponse;
import com.acme.salary.reporting.dto.PayReportCriteria;
import com.acme.salary.reporting.dto.PaySummaryResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Service
public class ReportingService {

    private final EmployeeService employeeService;
    private final PayAggregator payAggregator;

    public ReportingService(EmployeeService employeeService, PayAggregator payAggregator) {
        this.employeeService = employeeService;
        this.payAggregator = payAggregator;
    }

    public PaySummaryResponse summarize(PayReportCriteria criteria) {
        List<EmployeeSalaryProjection> rows = matchingRows(criteria);
        List<CurrencyAggregate> byCurrency = payAggregator.aggregate(rows);
        return new PaySummaryResponse(rows.size(), byCurrency);
    }

    public List<GroupBreakdownResponse> breakdownBy(BreakdownDimension dimension, PayReportCriteria criteria) {
        Map<String, List<EmployeeSalaryProjection>> rowsByGroup = matchingRows(criteria).stream().collect(groupingBy(
                dimension::groupKeyOf,
                () -> new TreeMap<>(dimension.groupOrder()),
                toList()));
        return rowsByGroup.entrySet().stream()
                .map(group -> new GroupBreakdownResponse(group.getKey(), payAggregator.aggregate(group.getValue())))
                .toList();
    }

    private List<EmployeeSalaryProjection> matchingRows(PayReportCriteria criteria) {
        return employeeService.findAllForReporting().stream()
                .filter(criteria::matches)
                .toList();
    }
}
