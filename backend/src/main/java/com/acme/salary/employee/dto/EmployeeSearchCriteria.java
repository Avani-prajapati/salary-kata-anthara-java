package com.acme.salary.employee.dto;

import com.acme.salary.employee.Department;
import com.acme.salary.employee.JobLevel;

import java.util.Optional;

public record EmployeeSearchCriteria(
        Optional<String> name,
        Optional<Department> department,
        Optional<String> countryCode,
        Optional<JobLevel> jobLevel) {

    public static EmployeeSearchCriteria unfiltered() {
        return new EmployeeSearchCriteria(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
