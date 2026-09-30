package com.acme.salary.reporting.dto;

import com.acme.salary.employee.Department;
import com.acme.salary.employee.EmployeeSalaryProjection;
import com.acme.salary.employee.JobLevel;

import java.util.Optional;

public record PayReportCriteria(
        Optional<Department> department,
        Optional<String> countryCode,
        Optional<JobLevel> jobLevel) {

    public static PayReportCriteria unfiltered() {
        return new PayReportCriteria(Optional.empty(), Optional.empty(), Optional.empty());
    }

    public boolean matches(EmployeeSalaryProjection row) {
        return department.map(row.getDepartment()::equals).orElse(true)
                && countryCode.map(row.getCountryCode()::equals).orElse(true)
                && jobLevel.map(row.getJobLevel()::equals).orElse(true);
    }
}
