package com.acme.salary.employee.dto;

import com.acme.salary.employee.Department;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.JobLevel;

import java.math.BigDecimal;

public record EmployeeSummaryResponse(
        Long id,
        String name,
        Department department,
        JobLevel jobLevel,
        String countryCode,
        BigDecimal salaryAmount,
        String salaryCurrency) {

    public static EmployeeSummaryResponse from(Employee employee) {
        return new EmployeeSummaryResponse(
                employee.getId(),
                employee.getName(),
                employee.getDepartment(),
                employee.getJobLevel(),
                employee.getCountryCode(),
                employee.getCurrentSalary().amount(),
                employee.getCurrentSalary().currencyCode());
    }
}
