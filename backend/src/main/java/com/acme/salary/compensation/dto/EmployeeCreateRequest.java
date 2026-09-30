package com.acme.salary.compensation.dto;

import com.acme.salary.employee.Department;
import com.acme.salary.employee.JobLevel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeCreateRequest(
        @NotBlank String name,
        @NotNull Department department,
        @NotNull JobLevel jobLevel,
        @NotBlank String countryCode,
        @NotNull @PastOrPresent LocalDate hireDate,
        Long managerId,
        @NotNull @DecimalMin("0.01") BigDecimal startingSalaryAmount,
        @NotBlank @Size(min = 3, max = 3) String startingSalaryCurrency) {
}
