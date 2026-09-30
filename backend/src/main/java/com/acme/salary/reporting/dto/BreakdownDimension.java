package com.acme.salary.reporting.dto;

import com.acme.salary.employee.Department;
import com.acme.salary.employee.EmployeeSalaryProjection;
import com.acme.salary.employee.JobLevel;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;

public enum BreakdownDimension {
    DEPARTMENT("department", row -> row.getDepartment().name(), Comparator.comparing(Department::valueOf)),
    COUNTRY("country", EmployeeSalaryProjection::getCountryCode, Comparator.naturalOrder()),
    JOB_LEVEL("jobLevel", row -> row.getJobLevel().name(), Comparator.comparing(JobLevel::valueOf));

    private final String queryValue;
    private final Function<EmployeeSalaryProjection, String> groupKey;
    private final Comparator<String> groupOrder;

    BreakdownDimension(String queryValue, Function<EmployeeSalaryProjection, String> groupKey, Comparator<String> groupOrder) {
        this.queryValue = queryValue;
        this.groupKey = groupKey;
        this.groupOrder = groupOrder;
    }

    public static BreakdownDimension fromQueryValue(String value) {
        return Arrays.stream(values())
                .filter(dimension -> dimension.queryValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported breakdown dimension: " + value));
    }

    public String groupKeyOf(EmployeeSalaryProjection row) {
        return groupKey.apply(row);
    }

    public Comparator<String> groupOrder() {
        return groupOrder;
    }
}
