package com.acme.salary.compensation;

import com.acme.salary.common.error.EntityNotFoundException;
import com.acme.salary.common.error.InvalidReferenceException;
import com.acme.salary.common.money.Money;
import com.acme.salary.compensation.dto.EmployeeCreateRequest;
import com.acme.salary.compensation.dto.SalaryChangeRequest;
import com.acme.salary.compensation.dto.SalaryHistoryEntryResponse;
import com.acme.salary.employee.CountryCode;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.dto.EmployeeSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

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

    @Transactional
    public EmployeeSummaryResponse hireEmployee(EmployeeCreateRequest request) {
        Money startingSalary = Money.of(request.startingSalaryAmount(), request.startingSalaryCurrency());
        Long managerId = request.managerId();
        if (managerId != null && !employeeRepository.existsById(managerId)) {
            throw new InvalidReferenceException("managerId", "Employee " + managerId + " was not found");
        }
        Employee employee = employeeRepository.save(new Employee(
                request.name(),
                request.department(),
                request.jobLevel(),
                CountryCode.of(request.countryCode()),
                request.hireDate(),
                managerId,
                startingSalary,
                clock.instant()));
        salaryHistoryRepository.save(new SalaryHistory(employee.getId(), startingSalary, request.hireDate(), clock.instant()));
        return EmployeeSummaryResponse.from(employee);
    }

    public List<SalaryHistoryEntryResponse> listHistory(Long employeeId) {
        findEmployeeOrThrow(employeeId);
        return salaryHistoryRepository.findByEmployeeIdOrderByEffectiveDateDesc(employeeId)
                .stream()
                .map(SalaryHistoryEntryResponse::from)
                .toList();
    }

    private Employee findEmployeeOrThrow(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee", employeeId));
    }
}
