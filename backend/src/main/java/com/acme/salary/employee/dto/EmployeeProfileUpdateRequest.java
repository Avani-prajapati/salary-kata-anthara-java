package com.acme.salary.employee.dto;

import com.acme.salary.employee.Department;
import com.acme.salary.employee.JobLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

public record EmployeeProfileUpdateRequest(
        @NotNull Department department,
        @NotNull JobLevel jobLevel,
        @NotBlank String countryCode,
        @NotNull @PastOrPresent LocalDate hireDate,
        Long managerId) {
}
