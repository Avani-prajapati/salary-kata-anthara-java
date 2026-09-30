package com.acme.salary.reporting;

import com.acme.salary.employee.Department;
import com.acme.salary.employee.EmployeeSalaryProjection;
import com.acme.salary.employee.JobLevel;

import java.math.BigDecimal;

record SalaryRow(
        Department getDepartment,
        String getCountryCode,
        JobLevel getJobLevel,
        String getSalaryCurrencyCode,
        BigDecimal getSalaryAmount) implements EmployeeSalaryProjection {

    static SalaryRow paid(String amount, String currencyCode) {
        return new SalaryRow(Department.ENGINEERING, "US", JobLevel.MID, currencyCode, new BigDecimal(amount));
    }

    static SalaryRow paid(Department department, String countryCode, JobLevel jobLevel, String amount, String currencyCode) {
        return new SalaryRow(department, countryCode, jobLevel, currencyCode, new BigDecimal(amount));
    }
}
