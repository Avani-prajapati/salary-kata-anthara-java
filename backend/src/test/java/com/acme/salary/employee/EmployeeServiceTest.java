package com.acme.salary.employee;

import com.acme.salary.common.money.Money;
import com.acme.salary.common.paging.PagedResponse;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeServiceTest {

    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final EmployeeService employeeService = new EmployeeService(employeeRepository);

    @Test
    void search_returns_each_employee_as_a_summary_with_salary_amount_and_currency() {
        Employee janeIto = employee(7L, "Jane Ito", Department.ENGINEERING, JobLevel.SENIOR, "JP", Money.of(new BigDecimal("9800000.00"), "JPY"));
        when(employeeRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(janeIto), PageRequest.of(0, 25), 1));

        PagedResponse<EmployeeSummaryResponse> result = employeeService.search(EmployeeSearchCriteria.unfiltered(), PageRequest.of(0, 25));

        assertThat(result.items()).containsExactly(new EmployeeSummaryResponse(
                7L, "Jane Ito", Department.ENGINEERING, JobLevel.SENIOR, "JP", new BigDecimal("9800000.00"), "JPY"));
    }

    @Test
    void search_reports_paging_metadata_for_the_requested_page() {
        when(employeeRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 25), 10_000));

        PagedResponse<EmployeeSummaryResponse> result = employeeService.search(EmployeeSearchCriteria.unfiltered(), PageRequest.of(2, 25));

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(25);
        assertThat(result.totalElements()).isEqualTo(10_000);
        assertThat(result.totalPages()).isEqualTo(400);
    }

    @Test
    void search_queries_the_repository_with_the_requested_page_and_size() {
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
}
