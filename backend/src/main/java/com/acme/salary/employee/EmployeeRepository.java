package com.acme.salary.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    @Query("""
            select e.department as department,
                   e.countryCode as countryCode,
                   e.jobLevel as jobLevel,
                   e.currentSalary.currencyCode as salaryCurrencyCode,
                   e.currentSalary.amount as salaryAmount
            from Employee e
            """)
    List<EmployeeSalaryProjection> findAllProjections();
}
