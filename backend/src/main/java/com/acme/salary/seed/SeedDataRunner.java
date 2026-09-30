package com.acme.salary.seed;

import com.acme.salary.compensation.SalaryHistory;
import com.acme.salary.compensation.SalaryHistoryRepository;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Component
@Profile("seed")
public class SeedDataRunner implements ApplicationRunner {

    static final int EMPLOYEE_COUNT = 10_000;
    private static final long RANDOM_SEED = 20_260_930L;
    private static final int BATCH_SIZE = 500;
    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);

    private final EmployeeRepository employeeRepository;
    private final SalaryHistoryRepository salaryHistoryRepository;
    private final Clock clock;

    public SeedDataRunner(EmployeeRepository employeeRepository, SalaryHistoryRepository salaryHistoryRepository, Clock clock) {
        this.employeeRepository = employeeRepository;
        this.salaryHistoryRepository = salaryHistoryRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long existingEmployees = employeeRepository.count();
        if (existingEmployees > 0) {
            log.info("Skipping seed: database already holds {} employees", existingEmployees);
            return;
        }
        List<SeedEmployee> generated = new SeedDataGenerator(clock).generate(EMPLOYEE_COUNT, RANDOM_SEED);
        List<Long> savedIdsByIndex = new ArrayList<>(generated.size());
        batchesWithinOneLevel(generated).forEach(batch -> saveBatch(batch, savedIdsByIndex));
        log.info("Seeded {} employees", savedIdsByIndex.size());
    }

    private static List<List<SeedEmployee>> batchesWithinOneLevel(List<SeedEmployee> generated) {
        Collection<List<SeedEmployee>> levels = generated.stream()
                .collect(groupingBy(SeedEmployee::jobLevel, LinkedHashMap::new, toList()))
                .values();
        return levels.stream().flatMap(level -> partition(level).stream()).toList();
    }

    private static List<List<SeedEmployee>> partition(List<SeedEmployee> level) {
        return IntStream.iterate(0, start -> start < level.size(), start -> start + BATCH_SIZE)
                .mapToObj(start -> level.subList(start, Math.min(start + BATCH_SIZE, level.size())))
                .toList();
    }

    private void saveBatch(List<SeedEmployee> batch, List<Long> savedIdsByIndex) {
        Instant now = clock.instant();
        List<Employee> saved = employeeRepository.saveAll(
                batch.stream().map(seed -> toEmployee(seed, savedIdsByIndex, now)).toList());
        saved.forEach(employee -> savedIdsByIndex.add(employee.getId()));
        salaryHistoryRepository.saveAll(saved.stream().map(employee -> initialSalaryHistory(employee, now)).toList());
    }

    private static Employee toEmployee(SeedEmployee seed, List<Long> savedIdsByIndex, Instant now) {
        Long managerId = seed.managerIndex().isPresent() ? savedIdsByIndex.get(seed.managerIndex().getAsInt()) : null;
        return new Employee(
                seed.name(),
                seed.department(),
                seed.jobLevel(),
                seed.country(),
                seed.hireDate(),
                managerId,
                seed.salary(),
                now);
    }

    private static SalaryHistory initialSalaryHistory(Employee employee, Instant now) {
        return new SalaryHistory(employee.getId(), employee.getCurrentSalary(), employee.getHireDate(), now);
    }
}
