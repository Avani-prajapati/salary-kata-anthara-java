package com.acme.salary.compensation;

import com.acme.salary.TemporarySqliteDatabase;
import com.acme.salary.common.money.Money;
import com.acme.salary.employee.CountryCode;
import com.acme.salary.employee.Department;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.JobLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class SalaryHistoryRepositoryDataJpaTest {

    @DynamicPropertySource
    static void sqliteDatabase(DynamicPropertyRegistry registry) {
        TemporarySqliteDatabase.register(registry);
    }

    @Autowired
    private SalaryHistoryRepository salaryHistoryRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void finds_the_latest_effective_dated_entry_even_when_inserted_out_of_order() {
        Long employeeId = persistEmployee();
        salaryHistoryRepository.save(historyRow(employeeId, "95000.00", LocalDate.of(2024, 6, 1), Instant.parse("2024-06-01T00:00:00Z")));
        salaryHistoryRepository.save(historyRow(employeeId, "80000.00", LocalDate.of(2022, 1, 1), Instant.parse("2024-09-01T00:00:00Z")));
        salaryHistoryRepository.save(historyRow(employeeId, "88000.00", LocalDate.of(2023, 1, 1), Instant.parse("2024-01-01T00:00:00Z")));

        SalaryHistory latest = salaryHistoryRepository.findTopByEmployeeIdOrderByEffectiveDateDesc(employeeId).orElseThrow();

        assertThat(latest.getAmount()).isEqualByComparingTo(new BigDecimal("95000.00"));
        assertThat(latest.getEffectiveDate()).isEqualTo(LocalDate.of(2024, 6, 1));
    }

    @Test
    void lists_all_history_entries_for_an_employee_newest_effective_date_first() {
        Long employeeId = persistEmployee();
        salaryHistoryRepository.save(historyRow(employeeId, "80000.00", LocalDate.of(2022, 1, 1), Instant.parse("2022-01-01T00:00:00Z")));
        salaryHistoryRepository.save(historyRow(employeeId, "95000.00", LocalDate.of(2024, 6, 1), Instant.parse("2024-06-01T00:00:00Z")));
        salaryHistoryRepository.save(historyRow(employeeId, "88000.00", LocalDate.of(2023, 1, 1), Instant.parse("2023-01-01T00:00:00Z")));

        List<SalaryHistory> history = salaryHistoryRepository.findByEmployeeIdOrderByEffectiveDateDesc(employeeId);

        assertThat(history).extracting(SalaryHistory::getEffectiveDate)
                .containsExactly(LocalDate.of(2024, 6, 1), LocalDate.of(2023, 1, 1), LocalDate.of(2022, 1, 1));
    }

    private Long persistEmployee() {
        Employee employee = new Employee(
                "Jane Ito", Department.ENGINEERING, JobLevel.SENIOR, CountryCode.of("JP"),
                LocalDate.of(2021, 3, 15), null, Money.of(new BigDecimal("9800000.00"), "JPY"),
                Instant.parse("2021-03-15T00:00:00Z"));
        return employeeRepository.save(employee).getId();
    }

    private static SalaryHistory historyRow(Long employeeId, String amount, LocalDate effectiveDate, Instant recordedAt) {
        return new SalaryHistory(employeeId, Money.of(new BigDecimal(amount), "USD"), effectiveDate, recordedAt);
    }
}
