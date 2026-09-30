package com.acme.salary.seed;

import com.acme.salary.common.money.Money;
import com.acme.salary.employee.CountryCode;
import com.acme.salary.employee.Department;
import com.acme.salary.employee.JobLevel;

import java.time.LocalDate;
import java.util.OptionalInt;

record SeedEmployee(
        int index,
        String name,
        Department department,
        JobLevel jobLevel,
        CountryCode country,
        LocalDate hireDate,
        OptionalInt managerIndex,
        Money salary) {
}
