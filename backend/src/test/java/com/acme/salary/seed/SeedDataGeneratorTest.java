package com.acme.salary.seed;

import com.acme.salary.employee.CountryCode;
import com.acme.salary.employee.Department;
import com.acme.salary.employee.JobLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SeedDataGeneratorTest {

    private static final long SEED = 42L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);

    private final SeedDataGenerator generator = new SeedDataGenerator(FIXED_CLOCK);
    private final List<SeedEmployee> tenThousand = generator.generate(10_000, SEED);

    @Test
    void generates_exactly_the_requested_number_of_employees() {
        assertThat(tenThousand).hasSize(10_000);
        assertThat(generator.generate(37, SEED)).hasSize(37);
    }

    @Test
    void the_same_seed_always_produces_identical_employees() {
        assertThat(generator.generate(10_000, SEED)).isEqualTo(tenThousand);
    }

    @Test
    void a_different_seed_produces_different_employees() {
        assertThat(generator.generate(10_000, SEED + 1)).isNotEqualTo(tenThousand);
    }

    @Test
    void every_manager_is_generated_before_the_employee_who_reports_to_them() {
        assertThat(tenThousand).allSatisfy(employee -> employee.managerIndex().ifPresent(managerIndex ->
                assertThat(managerIndex).isLessThan(employee.index())));
    }

    @Test
    void every_manager_is_strictly_more_senior_than_their_report() {
        assertThat(tenThousand).allSatisfy(employee -> employee.managerIndex().ifPresent(managerIndex ->
                assertThat(tenThousand.get(managerIndex).jobLevel()).isGreaterThan(employee.jobLevel())));
    }

    @Test
    void only_the_most_senior_employees_have_no_manager() {
        assertThat(tenThousand)
                .filteredOn(employee -> employee.managerIndex().isEmpty())
                .extracting(SeedEmployee::jobLevel)
                .containsOnly(JobLevel.EXECUTIVE);
    }

    @Test
    void employees_are_indexed_in_generation_order() {
        assertThat(tenThousand).extracting(SeedEmployee::index)
                .containsExactlyElementsOf(IntStream.range(0, 10_000).boxed().toList());
    }

    @Test
    void spans_every_department_and_job_level() {
        assertThat(tenThousand).extracting(SeedEmployee::department).containsOnly(Department.values());
        assertThat(tenThousand).extracting(SeedEmployee::jobLevel).containsOnly(JobLevel.values());
    }

    @Test
    void every_country_is_a_supported_country_and_several_countries_are_represented() {
        List<String> countries = tenThousand.stream().map(SeedEmployee::country).map(CountryCode::value).toList();

        assertThat(countries).allSatisfy(country -> assertThat(CountryCode.SUPPORTED).contains(country));
        assertThat(countries).containsAll(CountryCode.SUPPORTED);
    }

    @Test
    void mid_levels_make_up_the_bulk_of_the_workforce() {
        assertThat(tenThousand).filteredOn(employee -> employee.jobLevel() == JobLevel.MID).hasSize(3_000);
        assertThat(tenThousand).filteredOn(employee -> employee.jobLevel() == JobLevel.EXECUTIVE).hasSize(20);
    }

    @Test
    void every_salary_is_positive() {
        assertThat(tenThousand).allSatisfy(employee -> assertThat(employee.salary().amount()).isPositive());
    }

    @Test
    void salaries_rise_with_seniority_within_a_currency() {
        List<SeedEmployee> usEmployees = tenThousand.stream().filter(employee -> employee.country().value().equals("US")).toList();
        BigDecimal highestInternSalary = usEmployees.stream().filter(employee -> employee.jobLevel() == JobLevel.INTERN)
                .map(employee -> employee.salary().amount()).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal lowestExecutiveSalary = usEmployees.stream().filter(employee -> employee.jobLevel() == JobLevel.EXECUTIVE)
                .map(employee -> employee.salary().amount()).min(BigDecimal::compareTo).orElseThrow();

        assertThat(lowestExecutiveSalary).isGreaterThan(highestInternSalary);
    }

    @Test
    void salaries_are_paid_in_the_currency_of_the_employees_country() {
        assertThat(tenThousand).filteredOn(employee -> employee.country().value().equals("JP"))
                .allSatisfy(employee -> assertThat(employee.salary().currencyCode()).isEqualTo("JPY"));
        assertThat(tenThousand).filteredOn(employee -> employee.country().value().equals("DE"))
                .allSatisfy(employee -> assertThat(employee.salary().currencyCode()).isEqualTo("EUR"));
    }

    @Test
    void hire_dates_fall_within_the_last_fifteen_years_and_never_in_the_future() {
        assertThat(tenThousand).allSatisfy(employee ->
                assertThat(employee.hireDate()).isBetween(TODAY.minusYears(15), TODAY));
    }
}
