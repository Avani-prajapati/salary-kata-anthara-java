package com.acme.salary.employee;

import com.acme.salary.common.error.EntityNotFoundException;
import com.acme.salary.common.paging.PagedResponse;
import com.acme.salary.employee.dto.EmployeeDetailResponse;
import com.acme.salary.employee.dto.EmployeeSearchCriteria;
import com.acme.salary.employee.dto.EmployeeSummaryResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeController.class)
class EmployeeControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    void lists_employees_as_a_page_of_directory_rows() throws Exception {
        when(employeeService.search(any(), any())).thenReturn(new PagedResponse<>(
                List.of(new EmployeeSummaryResponse(7L, "Jane Ito", Department.ENGINEERING, JobLevel.SENIOR, "JP", new BigDecimal("9800000.00"), "JPY")),
                0, 25, 10_000, 400));

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.items[0].name").value("Jane Ito"))
                .andExpect(jsonPath("$.items[0].department").value("ENGINEERING"))
                .andExpect(jsonPath("$.items[0].jobLevel").value("SENIOR"))
                .andExpect(jsonPath("$.items[0].countryCode").value("JP"))
                .andExpect(jsonPath("$.items[0].salaryAmount").value(9800000.00))
                .andExpect(jsonPath("$.items[0].salaryCurrency").value("JPY"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(25))
                .andExpect(jsonPath("$.totalElements").value(10_000))
                .andExpect(jsonPath("$.totalPages").value(400));
    }

    @Test
    void defaults_to_the_first_page_of_twenty_five_employees() throws Exception {
        stubEmptyResult();

        mockMvc.perform(get("/api/employees")).andExpect(status().isOk());

        Pageable pageable = searchedPageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(25);
    }

    @Test
    void caps_the_page_size_at_the_configured_maximum_whatever_the_client_requests() throws Exception {
        stubEmptyResult();

        mockMvc.perform(get("/api/employees").param("size", "10000")).andExpect(status().isOk());

        assertThat(searchedPageable().getPageSize()).isEqualTo(100);
    }

    @Test
    void passes_the_requested_sort_column_and_direction_to_the_search() throws Exception {
        stubEmptyResult();

        mockMvc.perform(get("/api/employees").param("sort", "salaryAmount,desc")).andExpect(status().isOk());

        assertThat(searchedPageable().getSort()).containsExactly(Sort.Order.desc("salaryAmount"));
    }

    @Test
    void binds_name_department_country_and_job_level_filters_into_the_search_criteria() throws Exception {
        stubEmptyResult();

        mockMvc.perform(get("/api/employees")
                        .param("name", "  ito ")
                        .param("department", "ENGINEERING")
                        .param("countryCode", "jp")
                        .param("jobLevel", "SENIOR"))
                .andExpect(status().isOk());

        assertThat(searchedCriteria()).isEqualTo(new EmployeeSearchCriteria(
                Optional.of("ito"), Optional.of(Department.ENGINEERING), Optional.of("JP"), Optional.of(JobLevel.SENIOR)));
    }

    @Test
    void treats_a_blank_name_search_as_no_name_filter() throws Exception {
        stubEmptyResult();

        mockMvc.perform(get("/api/employees").param("name", "   ")).andExpect(status().isOk());

        assertThat(searchedCriteria()).isEqualTo(EmployeeSearchCriteria.unfiltered());
    }

    @Test
    void rejects_an_unsupported_country_filter_with_a_problem_detail() throws Exception {
        mockMvc.perform(get("/api/employees").param("countryCode", "ZZ"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("Unsupported country code: ZZ"));
    }

    @Test
    void rejects_an_unknown_department_filter_with_a_problem_detail() throws Exception {
        mockMvc.perform(get("/api/employees").param("department", "ASTRONAUTS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void returns_the_full_detail_for_an_existing_employee() throws Exception {
        when(employeeService.getById(7L)).thenReturn(new EmployeeDetailResponse(
                7L, "Jane Ito", Department.ENGINEERING, JobLevel.SENIOR, "JP",
                LocalDate.of(2021, 3, 15), 1L, "Priya Rao", new BigDecimal("9800000.00"), "JPY"));

        mockMvc.perform(get("/api/employees/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Jane Ito"))
                .andExpect(jsonPath("$.department").value("ENGINEERING"))
                .andExpect(jsonPath("$.jobLevel").value("SENIOR"))
                .andExpect(jsonPath("$.countryCode").value("JP"))
                .andExpect(jsonPath("$.hireDate").value("2021-03-15"))
                .andExpect(jsonPath("$.managerId").value(1))
                .andExpect(jsonPath("$.managerName").value("Priya Rao"))
                .andExpect(jsonPath("$.salaryAmount").value(9800000.00))
                .andExpect(jsonPath("$.salaryCurrency").value("JPY"));
    }

    @Test
    void returns_a_not_found_problem_detail_for_a_missing_employee() throws Exception {
        when(employeeService.getById(404L)).thenThrow(new EntityNotFoundException("Employee", 404L));

        mockMvc.perform(get("/api/employees/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Employee 404 was not found"));
    }

    private void stubEmptyResult() {
        when(employeeService.search(any(), any())).thenReturn(new PagedResponse<>(List.of(), 0, 25, 0, 0));
    }

    private Pageable searchedPageable() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(employeeService).search(any(), pageable.capture());
        return pageable.getValue();
    }

    private EmployeeSearchCriteria searchedCriteria() {
        ArgumentCaptor<EmployeeSearchCriteria> criteria = ArgumentCaptor.forClass(EmployeeSearchCriteria.class);
        verify(employeeService).search(criteria.capture(), any());
        return criteria.getValue();
    }
}
