package com.acme.salary.seed;

import com.acme.salary.common.money.Money;
import com.acme.salary.employee.Department;
import com.acme.salary.employee.JobLevel;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.SequencedMap;
import java.util.SplittableRandom;

final class SeedDataGenerator {

    private static final int PER_MILLE = 1000;
    private static final int HIRE_WINDOW_DAYS = 15 * 365;
    private static final int SALARY_ROUNDING_STEP = 100;
    private static final JobLevel LEVEL_ABSORBING_ROUNDING_REMAINDER = JobLevel.MID;

    private static final Map<JobLevel, Integer> LEVEL_SHARE_PER_MILLE = new EnumMap<>(Map.of(
            JobLevel.EXECUTIVE, 2,
            JobLevel.VP, 8,
            JobLevel.DIRECTOR, 20,
            JobLevel.MANAGER, 60,
            JobLevel.LEAD, 100,
            JobLevel.SENIOR, 200,
            JobLevel.MID, 300,
            JobLevel.ENTRY, 230,
            JobLevel.INTERN, 80));

    private static final WeightedChoice<Department> DEPARTMENTS = new WeightedChoice<>(departmentWeights());
    private static final WeightedChoice<SeedCountry> COUNTRIES = new WeightedChoice<>(countryWeights());

    private final Clock clock;

    SeedDataGenerator(Clock clock) {
        this.clock = clock;
    }

    List<SeedEmployee> generate(int count, long seed) {
        Generation generation = new Generation(new SplittableRandom(seed), LocalDate.now(clock), count);
        headcountBySeniorityDescending(count).forEach(generation::addLevel);
        return List.copyOf(generation.employees);
    }

    private static SequencedMap<JobLevel, Integer> headcountBySeniorityDescending(int count) {
        SequencedMap<JobLevel, Integer> headcount = new LinkedHashMap<>();
        Arrays.asList(JobLevel.values()).reversed()
                .forEach(level -> headcount.put(level, count * LEVEL_SHARE_PER_MILLE.get(level) / PER_MILLE));
        int assigned = headcount.values().stream().mapToInt(Integer::intValue).sum();
        headcount.merge(LEVEL_ABSORBING_ROUNDING_REMAINDER, count - assigned, Integer::sum);
        return headcount;
    }

    private static SequencedMap<Department, Integer> departmentWeights() {
        SequencedMap<Department, Integer> weights = new LinkedHashMap<>();
        weights.put(Department.ENGINEERING, 30);
        weights.put(Department.SALES, 15);
        weights.put(Department.CUSTOMER_SUPPORT, 12);
        weights.put(Department.OPERATIONS, 10);
        weights.put(Department.PRODUCT, 8);
        weights.put(Department.MARKETING, 8);
        weights.put(Department.DESIGN, 5);
        weights.put(Department.FINANCE, 5);
        weights.put(Department.HUMAN_RESOURCES, 4);
        weights.put(Department.LEGAL, 3);
        return weights;
    }

    private static SequencedMap<SeedCountry, Integer> countryWeights() {
        SequencedMap<SeedCountry, Integer> weights = new LinkedHashMap<>();
        Arrays.stream(SeedCountry.values()).forEach(country -> weights.put(country, country.weight()));
        return weights;
    }

    private static final class Generation {

        private final SplittableRandom random;
        private final LocalDate today;
        private final List<SeedEmployee> employees;

        private Generation(SplittableRandom random, LocalDate today, int expectedCount) {
            this.random = random;
            this.today = today;
            this.employees = new ArrayList<>(expectedCount);
        }

        private void addLevel(JobLevel level, int headcount) {
            int moreSeniorHeadcount = employees.size();
            for (int generated = 0; generated < headcount; generated++) {
                employees.add(nextEmployee(level, moreSeniorHeadcount));
            }
        }

        private SeedEmployee nextEmployee(JobLevel level, int moreSeniorHeadcount) {
            SeedCountry country = COUNTRIES.pick(random);
            return new SeedEmployee(
                    employees.size(),
                    NamePool.draw(random),
                    DEPARTMENTS.pick(random),
                    level,
                    country.countryCode(),
                    today.minusDays(random.nextInt(HIRE_WINDOW_DAYS + 1)),
                    managerAmong(moreSeniorHeadcount),
                    salaryFor(level, country.currencyCode()));
        }

        private OptionalInt managerAmong(int moreSeniorHeadcount) {
            return moreSeniorHeadcount == 0 ? OptionalInt.empty() : OptionalInt.of(random.nextInt(moreSeniorHeadcount));
        }

        private Money salaryFor(JobLevel level, String currencyCode) {
            SalaryBandTable.SalaryRange range = SalaryBandTable.rangeFor(level, currencyCode);
            long steps = (range.maximum() - range.minimum()) / SALARY_ROUNDING_STEP;
            long amount = range.minimum() + random.nextLong(steps + 1) * SALARY_ROUNDING_STEP;
            return Money.of(BigDecimal.valueOf(amount).setScale(2), currencyCode);
        }
    }
}
