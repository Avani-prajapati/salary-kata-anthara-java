package com.acme.salary.employee;

import com.acme.salary.TemporarySqliteDatabase;
import com.acme.salary.common.money.Money;
import com.acme.salary.employee.dto.EmployeeSearchCriteria;
import com.acme.salary.employee.dto.EmployeeSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class EmployeeRepositorySpecificationDataJpaTest {

    @DynamicPropertySource
    static void sqliteDatabase(DynamicPropertyRegistry registry) {
        TemporarySqliteDatabase.register(registry);
    }

    @Autowired
    private EmployeeRepository employeeRepository;

    @BeforeEach
    void seedAHandfulOfEmployees() {
        employeeRepository.saveAll(List.of(
                employee("Jane Ito", Department.ENGINEERING, JobLevel.SENIOR, "JP", "9800000.00", "JPY"),
                employee("Kaito Tanaka", Department.SALES, JobLevel.MID, "JP", "6200000.00", "JPY"),
                employee("Ben Miller", Department.ENGINEERING, JobLevel.MID, "US", "105000.50", "USD"),
                employee("Emma Schmidt", Department.FINANCE, JobLevel.SENIOR, "DE", "82000.00", "EUR"),
                employee("Ravi Iyer", Department.ENGINEERING, JobLevel.SENIOR, "IN", "3100000.00", "INR")));
    }

    @Test
    void name_search_is_a_case_insensitive_partial_match() {
        assertThat(namesMatching(criteria().withName("ITO"))).containsExactlyInAnyOrder("Jane Ito", "Kaito Tanaka");
    }

    @Test
    void name_search_treats_like_wildcards_as_literal_text() {
        assertThat(namesMatching(criteria().withName("%"))).isEmpty();
    }

    @Test
    void filters_by_department() {
        assertThat(namesMatching(criteria().withDepartment(Department.ENGINEERING)))
                .containsExactlyInAnyOrder("Jane Ito", "Ben Miller", "Ravi Iyer");
    }

    @Test
    void filters_by_country() {
        assertThat(namesMatching(criteria().withCountry("JP"))).containsExactlyInAnyOrder("Jane Ito", "Kaito Tanaka");
    }

    @Test
    void filters_by_job_level() {
        assertThat(namesMatching(criteria().withJobLevel(JobLevel.MID))).containsExactlyInAnyOrder("Kaito Tanaka", "Ben Miller");
    }

    @Test
    void combined_filters_narrow_to_employees_matching_all_of_them() {
        EmployeeSearchCriteria engineeringSeniorsInJapan = criteria()
                .withDepartment(Department.ENGINEERING).withJobLevel(JobLevel.SENIOR).withCountry("JP").build();

        assertThat(namesMatching(engineeringSeniorsInJapan)).containsExactly("Jane Ito");
    }

    @Test
    void a_filter_combination_matching_nobody_returns_an_empty_page() {
        EmployeeSearchCriteria legalInBrazil = criteria().withDepartment(Department.LEGAL).withCountry("BR").build();

        Page<Employee> result = employeeRepository.findAll(EmployeeSpecifications.matching(legalInBrazil), PageRequest.of(0, 25));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void sorting_by_salary_descending_orders_by_numeric_amount() {
        List<String> names = searchNames(PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "salaryAmount")));

        assertThat(names).containsExactly("Jane Ito", "Kaito Tanaka", "Ravi Iyer", "Ben Miller", "Emma Schmidt");
    }

    @Test
    void sorting_by_name_ascending_and_descending() {
        assertThat(searchNames(PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "name"))))
                .containsExactly("Ben Miller", "Emma Schmidt", "Jane Ito", "Kaito Tanaka", "Ravi Iyer");
        assertThat(searchNames(PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "name"))))
                .containsExactly("Ravi Iyer", "Kaito Tanaka", "Jane Ito", "Emma Schmidt", "Ben Miller");
    }

    @Test
    void sorting_by_country_ascending() {
        assertThat(searchNames(PageRequest.of(0, 25, Sort.by("countryCode", "name"))))
                .containsExactly("Emma Schmidt", "Ravi Iyer", "Jane Ito", "Kaito Tanaka", "Ben Miller");
    }

    @Test
    void a_page_never_holds_more_rows_than_its_page_size() {
        Page<Employee> firstPage = employeeRepository.findAll(EmployeeSpecifications.matching(EmployeeSearchCriteria.unfiltered()), PageRequest.of(0, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
    }

    @Test
    void salary_amounts_round_trip_as_exact_decimals() {
        List<EmployeeSummaryResponse> usEmployees = new EmployeeService(employeeRepository)
                .search(criteria().withCountry("US").build(), PageRequest.of(0, 25)).items();

        assertThat(usEmployees.getFirst().salaryAmount()).isEqualByComparingTo(new BigDecimal("105000.50"));
        assertThat(usEmployees.getFirst().salaryCurrency()).isEqualTo("USD");
    }

    private List<String> namesMatching(CriteriaBuilder criteria) {
        return namesMatching(criteria.build());
    }

    private List<String> namesMatching(EmployeeSearchCriteria criteria) {
        return employeeRepository.findAll(EmployeeSpecifications.matching(criteria)).stream().map(Employee::getName).toList();
    }

    private List<String> searchNames(PageRequest pageRequest) {
        return new EmployeeService(employeeRepository).search(EmployeeSearchCriteria.unfiltered(), pageRequest)
                .items().stream().map(EmployeeSummaryResponse::name).toList();
    }

    private static Employee employee(String name, Department department, JobLevel jobLevel, String country, String amount, String currency) {
        return new Employee(name, department, jobLevel, CountryCode.of(country), LocalDate.of(2021, 3, 15), null,
                Money.of(new BigDecimal(amount), currency), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static CriteriaBuilder criteria() {
        return new CriteriaBuilder();
    }

    private static final class CriteriaBuilder {
        private Optional<String> name = Optional.empty();
        private Optional<Department> department = Optional.empty();
        private Optional<String> country = Optional.empty();
        private Optional<JobLevel> jobLevel = Optional.empty();

        CriteriaBuilder withName(String value) {
            name = Optional.of(value);
            return this;
        }

        CriteriaBuilder withDepartment(Department value) {
            department = Optional.of(value);
            return this;
        }

        CriteriaBuilder withCountry(String value) {
            country = Optional.of(value);
            return this;
        }

        CriteriaBuilder withJobLevel(JobLevel value) {
            jobLevel = Optional.of(value);
            return this;
        }

        EmployeeSearchCriteria build() {
            return new EmployeeSearchCriteria(name, department, country, jobLevel);
        }
    }
}
