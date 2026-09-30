package com.acme.salary.employee.dto;

import com.acme.salary.employee.Department;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.JobLevel;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeDetailResponse(
        Long id,
        String name,
        Department department,
        JobLevel jobLevel,
        String countryCode,
        LocalDate hireDate,
        Long managerId,
        String managerName,
        BigDecimal salaryAmount,
        String salaryCurrency) {

    public static EmployeeDetailResponse from(Employee employee, String managerName) {
        return new EmployeeDetailResponse(
                employee.getId(),
                employee.getName(),
                employee.getDepartment(),
                employee.getJobLevel(),
                employee.getCountryCode(),
                employee.getHireDate(),
                employee.getManagerId(),
                managerName,
                employee.getCurrentSalary().amount(),
                employee.getCurrentSalary().currencyCode());
    }
}
