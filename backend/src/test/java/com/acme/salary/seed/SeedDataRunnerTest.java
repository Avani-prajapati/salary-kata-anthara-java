package com.acme.salary.seed;

import com.acme.salary.TemporarySqliteDatabase;
import com.acme.salary.common.money.Money;
import com.acme.salary.compensation.SalaryHistory;
import com.acme.salary.compensation.SalaryHistoryRepository;
import com.acme.salary.employee.CountryCode;
import com.acme.salary.employee.Department;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.JobLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class SeedDataRunnerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);

    @DynamicPropertySource
    static void sqliteDatabase(DynamicPropertyRegistry registry) {
        TemporarySqliteDatabase.register(registry);
    }

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalaryHistoryRepository salaryHistoryRepository;

    @Test
    void seeding_a_fresh_database_twice_inserts_ten_thousand_employees_exactly_once() {
        SeedDataRunner runner = new SeedDataRunner(employeeRepository, salaryHistoryRepository, FIXED_CLOCK);

        runner.run(new DefaultApplicationArguments());
        runner.run(new DefaultApplicationArguments());

        assertThat(employeeRepository.count()).isEqualTo(10_000);
    }

    @Test
    void every_seeded_employee_gets_one_initial_salary_history_record() {
        new SeedDataRunner(employeeRepository, salaryHistoryRepository, FIXED_CLOCK).run(new DefaultApplicationArguments());

        assertThat(salaryHistoryRepository.count()).isEqualTo(10_000);
    }

    @Test
    void the_initial_salary_history_record_matches_the_employees_current_salary_effective_from_hire() {
        new SeedDataRunner(employeeRepository, salaryHistoryRepository, FIXED_CLOCK).run(new DefaultApplicationArguments());

        SalaryHistory firstRecord = salaryHistoryRepository.findAll().getFirst();
        Employee employee = employeeRepository.findById(firstRecord.getEmployeeId()).orElseThrow();
        assertThat(firstRecord.getAmount()).isEqualByComparingTo(employee.getCurrentSalary().amount());
        assertThat(firstRecord.getCurrencyCode()).isEqualTo(employee.getCurrentSalary().currencyCode());
        assertThat(firstRecord.getEffectiveDate()).isEqualTo(employee.getHireDate());
    }

    @Test
    void every_seeded_manager_reference_points_at_an_existing_employee() {
        new SeedDataRunner(employeeRepository, salaryHistoryRepository, FIXED_CLOCK).run(new DefaultApplicationArguments());

        assertThat(employeeRepository.findAll())
                .filteredOn(employee -> employee.getManagerId() != null)
                .allSatisfy(employee -> assertThat(employee.getManagerId()).isLessThan(employee.getId()));
    }

    @Test
    void seeding_a_database_that_already_holds_employees_changes_nothing() {
        employeeRepository.save(new Employee("Existing Person", Department.LEGAL, JobLevel.MID, CountryCode.of("GB"),
                LocalDate.of(2020, 5, 1), null, Money.of(new BigDecimal("60000.00"), "GBP"), FIXED_CLOCK.instant()));

        new SeedDataRunner(employeeRepository, salaryHistoryRepository, FIXED_CLOCK).run(new DefaultApplicationArguments());

        assertThat(employeeRepository.count()).isEqualTo(1);
        assertThat(salaryHistoryRepository.count()).isZero();
    }
}
