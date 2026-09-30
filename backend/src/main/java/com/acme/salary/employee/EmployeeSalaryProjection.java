package com.acme.salary.employee;

import java.math.BigDecimal;

public interface EmployeeSalaryProjection {

    Department getDepartment();

    String getCountryCode();

    JobLevel getJobLevel();

    String getSalaryCurrencyCode();

    BigDecimal getSalaryAmount();
}
