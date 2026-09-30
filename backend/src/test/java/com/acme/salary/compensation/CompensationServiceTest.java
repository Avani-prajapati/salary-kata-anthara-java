package com.acme.salary.compensation;

import com.acme.salary.common.error.EntityNotFoundException;
import com.acme.salary.common.error.InvalidReferenceException;
import com.acme.salary.common.money.Money;
import com.acme.salary.compensation.dto.EmployeeCreateRequest;
import com.acme.salary.compensation.dto.SalaryChangeRequest;
import com.acme.salary.compensation.dto.SalaryHistoryEntryResponse;
import com.acme.salary.employee.CountryCode;
import com.acme.salary.employee.Department;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.JobLevel;
import com.acme.salary.employee.dto.EmployeeSummaryResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompensationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2024-06-01T00:00:00Z"), ZoneOffset.UTC);

    private final SalaryHistoryRepository salaryHistoryRepository = mock(SalaryHistoryRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final CompensationService compensationService =
            new CompensationService(salaryHistoryRepository, employeeRepository, FIXED_CLOCK);

    @Test
    void changeSalary_inserts_a_new_history_row_and_updates_the_employees_current_salary_to_match() {
        Employee employee = employee(7L, Money.of(new BigDecimal("90000.00"), "USD"));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        when(salaryHistoryRepository.save(any(SalaryHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDate effectiveDate = LocalDate.of(2024, 6, 1);
        when(salaryHistoryRepository.findTopByEmployeeIdOrderByEffectiveDateDesc(7L)).thenReturn(Optional.of(
                new SalaryHistory(7L, Money.of(new BigDecimal("100000.00"), "USD"), effectiveDate, FIXED_CLOCK.instant())));
        SalaryChangeRequest raise = new SalaryChangeRequest(new BigDecimal("100000.00"), "USD", effectiveDate);

        SalaryHistoryEntryResponse response = compensationService.changeSalary(7L, raise);

        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(response.currencyCode()).isEqualTo("USD");
        assertThat(response.effectiveDate()).isEqualTo(effectiveDate);
        assertThat(response.recordedAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(employee.getCurrentSalary().amount()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(employee.getCurrentSalary().currencyCode()).isEqualTo("USD");

        ArgumentCaptor<SalaryHistory> insertedEntry = ArgumentCaptor.forClass(SalaryHistory.class);
        verify(salaryHistoryRepository).save(insertedEntry.capture());
        assertThat(insertedEntry.getValue().getEmployeeId()).isEqualTo(7L);
        assertThat(insertedEntry.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("100000.00"));
        verify(employeeRepository).save(employee);
    }

    @Test
    void changeSalary_rejects_a_zero_or_negative_amount_before_any_row_is_written() {
        Employee employee = employee(7L, Money.of(new BigDecimal("90000.00"), "USD"));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        SalaryChangeRequest zeroAmount = new SalaryChangeRequest(BigDecimal.ZERO, "USD", LocalDate.of(2024, 6, 1));

        assertThatThrownBy(() -> compensationService.changeSalary(7L, zeroAmount))
                .isInstanceOf(IllegalArgumentException.class);

        verify(salaryHistoryRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void changeSalary_throws_entity_not_found_for_a_missing_employee() {
        when(employeeRepository.findById(404L)).thenReturn(Optional.empty());
        SalaryChangeRequest request = new SalaryChangeRequest(new BigDecimal("100000.00"), "USD", LocalDate.of(2024, 6, 1));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> compensationService.changeSalary(404L, request));
        verify(salaryHistoryRepository, never()).save(any());
    }

    @Test
    void changeSalary_leaves_current_salary_pointing_at_the_later_effective_entry_when_backdated() {
        Employee employee = employee(7L, Money.of(new BigDecimal("90000.00"), "USD"));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        when(salaryHistoryRepository.save(any(SalaryHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SalaryHistory laterEffectiveEntry = new SalaryHistory(
                7L, Money.of(new BigDecimal("90000.00"), "USD"), LocalDate.of(2024, 6, 1), Instant.parse("2024-01-01T00:00:00Z"));
        when(salaryHistoryRepository.findTopByEmployeeIdOrderByEffectiveDateDesc(7L)).thenReturn(Optional.of(laterEffectiveEntry));
        SalaryChangeRequest backdatedCorrection = new SalaryChangeRequest(
                new BigDecimal("80000.00"), "USD", LocalDate.of(2024, 1, 1));

        SalaryHistoryEntryResponse response = compensationService.changeSalary(7L, backdatedCorrection);

        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("80000.00"));
        assertThat(response.effectiveDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(employee.getCurrentSalary().amount()).isEqualByComparingTo(new BigDecimal("90000.00"));
        assertThat(employee.getCurrentSalary().currencyCode()).isEqualTo("USD");
    }

    @Test
    void listHistory_returns_a_single_record_for_a_brand_new_employee() {
        when(employeeRepository.findById(3L)).thenReturn(Optional.of(employee(3L, Money.of(new BigDecimal("50000.00"), "USD"))));
        SalaryHistory singleEntry = new SalaryHistory(
                3L, Money.of(new BigDecimal("50000.00"), "USD"), LocalDate.of(2024, 1, 1), Instant.parse("2024-01-01T00:00:00Z"));
        when(salaryHistoryRepository.findByEmployeeIdOrderByEffectiveDateDesc(3L)).thenReturn(Arrays.asList(singleEntry));

        java.util.List<SalaryHistoryEntryResponse> history = compensationService.listHistory(3L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).amount()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(history.get(0).currencyCode()).isEqualTo("USD");
    }

    @Test
    void listHistory_returns_multiple_records_in_newest_effective_date_first_order() {
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee(7L, Money.of(new BigDecimal("100000.00"), "USD"))));
        SalaryHistory earlier = new SalaryHistory(
                7L, Money.of(new BigDecimal("80000.00"), "USD"), LocalDate.of(2024, 1, 1), Instant.parse("2024-01-01T00:00:00Z"));
        SalaryHistory later = new SalaryHistory(
                7L, Money.of(new BigDecimal("100000.00"), "USD"), LocalDate.of(2024, 6, 1), Instant.parse("2024-06-01T00:00:00Z"));
        when(salaryHistoryRepository.findByEmployeeIdOrderByEffectiveDateDesc(7L)).thenReturn(Arrays.asList(later, earlier));

        java.util.List<SalaryHistoryEntryResponse> history = compensationService.listHistory(7L);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).effectiveDate()).isEqualTo(LocalDate.of(2024, 6, 1));
        assertThat(history.get(0).amount()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(history.get(1).effectiveDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(history.get(1).amount()).isEqualByComparingTo(new BigDecimal("80000.00"));
    }

    @Test
    void hireEmployee_creates_exactly_one_employee_and_one_matching_initial_history_row() {
        LocalDate hireDate = LocalDate.of(2024, 6, 1);
        EmployeeCreateRequest request = new EmployeeCreateRequest(
                "New Hire", Department.ENGINEERING, JobLevel.ENTRY, "US", hireDate, null,
                new BigDecimal("70000.00"), "USD");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        });
        when(salaryHistoryRepository.save(any(SalaryHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeSummaryResponse response = compensationService.hireEmployee(request);

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.name()).isEqualTo("New Hire");
        assertThat(response.salaryAmount()).isEqualByComparingTo(new BigDecimal("70000.00"));
        assertThat(response.salaryCurrency()).isEqualTo("USD");

        ArgumentCaptor<Employee> savedEmployee = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(savedEmployee.capture());
        assertThat(savedEmployee.getValue().getCurrentSalary().amount()).isEqualByComparingTo(new BigDecimal("70000.00"));
        assertThat(savedEmployee.getValue().getCurrentSalary().currencyCode()).isEqualTo("USD");

        ArgumentCaptor<SalaryHistory> savedHistory = ArgumentCaptor.forClass(SalaryHistory.class);
        verify(salaryHistoryRepository).save(savedHistory.capture());
        assertThat(savedHistory.getValue().getEmployeeId()).isEqualTo(9L);
        assertThat(savedHistory.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("70000.00"));
        assertThat(savedHistory.getValue().getCurrencyCode()).isEqualTo("USD");
        assertThat(savedHistory.getValue().getEffectiveDate()).isEqualTo(hireDate);
    }

    @Test
    void hireEmployee_rejects_a_zero_or_negative_starting_salary_before_any_row_is_written() {
        EmployeeCreateRequest request = new EmployeeCreateRequest(
                "New Hire", Department.ENGINEERING, JobLevel.ENTRY, "US",
                LocalDate.of(2024, 6, 1), null, BigDecimal.ZERO, "USD");

        assertThatThrownBy(() -> compensationService.hireEmployee(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(employeeRepository, never()).save(any());
        verify(salaryHistoryRepository, never()).save(any());
    }

    @Test
    void hireEmployee_rejects_an_unknown_manager_id() {
        EmployeeCreateRequest request = new EmployeeCreateRequest(
                "New Hire", Department.ENGINEERING, JobLevel.ENTRY, "US",
                LocalDate.of(2024, 6, 1), 999L, new BigDecimal("70000.00"), "USD");
        when(employeeRepository.existsById(999L)).thenReturn(false);

        assertThatExceptionOfType(InvalidReferenceException.class)
                .isThrownBy(() -> compensationService.hireEmployee(request))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("managerId"));

        verify(employeeRepository, never()).save(any());
        verify(salaryHistoryRepository, never()).save(any());
    }

    @Test
    void salary_history_repository_declares_no_custom_update_or_delete_method() {
        var declaredMethodNames = Arrays.stream(SalaryHistoryRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();

        assertThat(declaredMethodNames).noneMatch(CompensationServiceTest::impliesMutatingAnExistingRow);
    }

    private static boolean impliesMutatingAnExistingRow(String methodName) {
        String lowerCased = methodName.toLowerCase();
        return lowerCased.contains("update") || lowerCased.contains("delete") || lowerCased.contains("remove");
    }

    private static Employee employee(Long id, Money salary) {
        Employee employee = new Employee(
                "Jane Ito", Department.ENGINEERING, JobLevel.SENIOR, CountryCode.of("JP"),
                LocalDate.of(2020, 1, 1), null, salary, Instant.EPOCH);
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }
}
