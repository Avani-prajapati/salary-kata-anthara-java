package com.acme.salary.employee;

import com.acme.salary.common.error.EntityNotFoundException;
import com.acme.salary.common.error.InvalidReferenceException;
import com.acme.salary.common.money.Money;
import com.acme.salary.common.paging.PagedResponse;
import com.acme.salary.employee.dto.EmployeeDetailResponse;
import com.acme.salary.employee.dto.EmployeeProfileUpdateRequest;
import com.acme.salary.employee.dto.EmployeeSearchCriteria;
import com.acme.salary.employee.dto.EmployeeSummaryResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2024-06-01T00:00:00Z"), ZoneOffset.UTC);

    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final EmployeeService employeeService = new EmployeeService(employeeRepository, FIXED_CLOCK);

    @Test
    void employee_summaries_include_salary_amount_and_currency() {
        Employee janeIto = employee(7L, "Jane Ito", Department.ENGINEERING, JobLevel.SENIOR, "JP", Money.of(new BigDecimal("9800000.00"), "JPY"));
        when(employeeRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(janeIto), PageRequest.of(0, 25), 1));

        PagedResponse<EmployeeSummaryResponse> result = employeeService.search(EmployeeSearchCriteria.unfiltered(), PageRequest.of(0, 25));

        assertThat(result.items()).containsExactly(new EmployeeSummaryResponse(
                7L, "Jane Ito", Department.ENGINEERING, JobLevel.SENIOR, "JP", new BigDecimal("9800000.00"), "JPY"));
    }

    @Test
    void requesting_a_page_returns_matching_paging_metadata() {
        when(employeeRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 25), 10_000));

        PagedResponse<EmployeeSummaryResponse> result = employeeService.search(EmployeeSearchCriteria.unfiltered(), PageRequest.of(2, 25));

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(25);
        assertThat(result.totalElements()).isEqualTo(10_000);
        assertThat(result.totalPages()).isEqualTo(400);
    }

    @Test
    void the_requested_page_and_size_are_passed_through_to_the_repository_query() {
        stubEmptyPage();

        employeeService.search(EmployeeSearchCriteria.unfiltered(), PageRequest.of(3, 50));

        Pageable queried = queriedPageable();
        assertThat(queried.getPageNumber()).isEqualTo(3);
        assertThat(queried.getPageSize()).isEqualTo(50);
    }

    @Test
    void sorting_by_salary_orders_by_the_current_salary_amount_with_a_stable_tiebreaker() {
        stubEmptyPage();

        employeeService.search(EmployeeSearchCriteria.unfiltered(), PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "salaryAmount")));

        assertThat(queriedPageable().getSort())
                .containsExactly(Sort.Order.desc("currentSalary.amount"), Sort.Order.asc("id"));
    }

    @Test
    void sorting_by_name_department_country_and_job_level_is_supported_in_either_direction() {
        stubEmptyPage();
        Sort requested = Sort.by(Sort.Order.asc("name"), Sort.Order.desc("department"), Sort.Order.asc("countryCode"), Sort.Order.desc("jobLevel"));

        employeeService.search(EmployeeSearchCriteria.unfiltered(), PageRequest.of(0, 25, requested));

        assertThat(queriedPageable().getSort()).containsExactly(
                Sort.Order.asc("name"), Sort.Order.desc("department"), Sort.Order.asc("countryCode"), Sort.Order.desc("jobLevel"), Sort.Order.asc("id"));
    }

    @Test
    void unsorted_search_orders_employees_by_name() {
        stubEmptyPage();

        employeeService.search(EmployeeSearchCriteria.unfiltered(), PageRequest.of(0, 25));

        assertThat(queriedPageable().getSort()).containsExactly(Sort.Order.asc("name"), Sort.Order.asc("id"));
    }

    @Test
    void rejects_sorting_by_a_property_that_is_not_a_directory_column() {
        assertThatThrownBy(() -> employeeService.search(
                EmployeeSearchCriteria.unfiltered(), PageRequest.of(0, 25, Sort.by("managerId"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("managerId");
    }

    @Test
    void an_employees_detail_includes_their_managers_name() {
        Employee manager = employee(1L, "Priya Rao", Department.ENGINEERING, JobLevel.DIRECTOR, "IN", Money.of(new BigDecimal("18000000.00"), "INR"));
        Employee janeIto = employeeWithManager(7L, "Jane Ito", Department.ENGINEERING, JobLevel.SENIOR, "JP",
                LocalDate.of(2021, 3, 15), 1L, Money.of(new BigDecimal("9800000.00"), "JPY"));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(janeIto));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(manager));

        EmployeeDetailResponse detail = employeeService.getById(7L);

        assertThat(detail).isEqualTo(new EmployeeDetailResponse(
                7L, "Jane Ito", Department.ENGINEERING, JobLevel.SENIOR, "JP",
                LocalDate.of(2021, 3, 15), 1L, "Priya Rao", new BigDecimal("9800000.00"), "JPY"));
    }

    @Test
    void an_employee_without_a_manager_has_a_null_manager_name() {
        Employee janeIto = employee(7L, "Jane Ito", Department.ENGINEERING, JobLevel.SENIOR, "JP", Money.of(new BigDecimal("9800000.00"), "JPY"));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(janeIto));

        EmployeeDetailResponse detail = employeeService.getById(7L);

        assertThat(detail.managerId()).isNull();
        assertThat(detail.managerName()).isNull();
    }

    @Test
    void an_unknown_employee_id_reports_not_found() {
        when(employeeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getById(404L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void editable_profile_fields_are_persisted_without_touching_the_current_salary() {
        Employee janeIto = employee(7L, "Jane Ito", Department.ENGINEERING, JobLevel.SENIOR, "JP", Money.of(new BigDecimal("9800000.00"), "JPY"));
        Employee priyaRao = employee(1L, "Priya Rao", Department.ENGINEERING, JobLevel.DIRECTOR, "IN", Money.of(new BigDecimal("18000000.00"), "INR"));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(janeIto));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(priyaRao));
        when(employeeRepository.existsById(1L)).thenReturn(true);
        EmployeeProfileUpdateRequest request = new EmployeeProfileUpdateRequest(
                Department.PRODUCT, JobLevel.LEAD, "US", LocalDate.of(2022, 5, 1), 1L);

        EmployeeDetailResponse updated = employeeService.updateProfile(7L, request);

        assertThat(updated).isEqualTo(new EmployeeDetailResponse(
                7L, "Jane Ito", Department.PRODUCT, JobLevel.LEAD, "US",
                LocalDate.of(2022, 5, 1), 1L, "Priya Rao", new BigDecimal("9800000.00"), "JPY"));
        verify(employeeRepository).save(janeIto);
    }

    @Test
    void a_profile_update_for_an_unknown_employee_reports_not_found() {
        when(employeeRepository.findById(404L)).thenReturn(Optional.empty());
        EmployeeProfileUpdateRequest request = new EmployeeProfileUpdateRequest(
                Department.PRODUCT, JobLevel.LEAD, "US", LocalDate.of(2022, 5, 1), null);

        assertThatThrownBy(() -> employeeService.updateProfile(404L, request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void assigning_a_nonexistent_manager_id_is_rejected_without_writing_anything() {
        Employee janeIto = employee(7L, "Jane Ito", Department.ENGINEERING, JobLevel.SENIOR, "JP", Money.of(new BigDecimal("9800000.00"), "JPY"));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(janeIto));
        when(employeeRepository.existsById(999L)).thenReturn(false);
        EmployeeProfileUpdateRequest request = new EmployeeProfileUpdateRequest(
                Department.PRODUCT, JobLevel.LEAD, "US", LocalDate.of(2022, 5, 1), 999L);

        assertThatExceptionOfType(InvalidReferenceException.class)
                .isThrownBy(() -> employeeService.updateProfile(7L, request))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("managerId"));
        verify(employeeRepository, never()).save(any());
    }

    private void stubEmptyPage() {
        when(employeeRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(Page.empty());
    }

    private Pageable queriedPageable() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(employeeRepository).findAll(anySpecification(), pageable.capture());
        return pageable.getValue();
    }

    @SuppressWarnings("unchecked")
    private static Specification<Employee> anySpecification() {
        return any(Specification.class);
    }

    private static Employee employee(Long id, String name, Department department, JobLevel jobLevel, String country, Money salary) {
        Employee employee = new Employee(name, department, jobLevel, CountryCode.of(country), LocalDate.of(2020, 1, 1), null, salary, Instant.EPOCH);
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }

    private static Employee employeeWithManager(
            Long id, String name, Department department, JobLevel jobLevel, String country,
            LocalDate hireDate, Long managerId, Money salary) {
        Employee employee = new Employee(name, department, jobLevel, CountryCode.of(country), hireDate, managerId, salary, Instant.EPOCH);
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }
}
