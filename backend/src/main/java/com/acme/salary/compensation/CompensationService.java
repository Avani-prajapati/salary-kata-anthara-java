package com.acme.salary.compensation;

import com.acme.salary.common.error.EntityNotFoundException;
import com.acme.salary.common.money.Money;
import com.acme.salary.compensation.dto.SalaryChangeRequest;
import com.acme.salary.compensation.dto.SalaryHistoryEntryResponse;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class CompensationService {

    private final SalaryHistoryRepository salaryHistoryRepository;
    private final EmployeeRepository employeeRepository;
    private final Clock clock;

    public CompensationService(SalaryHistoryRepository salaryHistoryRepository, EmployeeRepository employeeRepository, Clock clock) {
        this.salaryHistoryRepository = salaryHistoryRepository;
        this.employeeRepository = employeeRepository;
        this.clock = clock;
    }

    @Transactional
    public SalaryHistoryEntryResponse changeSalary(Long employeeId, SalaryChangeRequest request) {
        Employee employee = findEmployeeOrThrow(employeeId);
        Money salary = Money.of(request.amount(), request.currencyCode());
        SalaryHistory entry = salaryHistoryRepository.save(
                new SalaryHistory(employeeId, salary, request.effectiveDate(), clock.instant()));
        applyCurrentSalary(employee, employeeId);
        return SalaryHistoryEntryResponse.from(entry);
    }

    private void applyCurrentSalary(Employee employee, Long employeeId) {
        SalaryHistory current = salaryHistoryRepository.findTopByEmployeeIdOrderByEffectiveDateDesc(employeeId)
                .orElseThrow(() -> new IllegalStateException("Employee " + employeeId + " has no salary history"));
        employee.recordSalaryChange(Money.of(current.getAmount(), current.getCurrencyCode()), clock.instant());
        employeeRepository.save(employee);
    }

    private Employee findEmployeeOrThrow(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee", employeeId));
    }
}
