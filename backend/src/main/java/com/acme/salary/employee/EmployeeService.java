package com.acme.salary.employee;

import com.acme.salary.common.error.EntityNotFoundException;
import com.acme.salary.common.error.InvalidReferenceException;
import com.acme.salary.common.paging.PagedResponse;
import com.acme.salary.employee.dto.EmployeeDetailResponse;
import com.acme.salary.employee.dto.EmployeeProfileUpdateRequest;
import com.acme.salary.employee.dto.EmployeeSearchCriteria;
import com.acme.salary.employee.dto.EmployeeSummaryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class EmployeeService {

    private static final Map<String, String> SORTABLE_PROPERTIES = Map.of(
            "name", "name",
            "department", "department",
            "jobLevel", "jobLevel",
            "countryCode", "countryCode",
            "salaryAmount", "currentSalary.amount");

    private static final Sort DEFAULT_SORT = Sort.by("name");
    private static final Sort STABLE_TIEBREAKER = Sort.by("id");

    private final EmployeeRepository employeeRepository;
    private final Clock clock;

    public EmployeeService(EmployeeRepository employeeRepository, Clock clock) {
        this.employeeRepository = employeeRepository;
        this.clock = clock;
    }

    public PagedResponse<EmployeeSummaryResponse> search(EmployeeSearchCriteria criteria, Pageable pageable) {
        Pageable entityPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), toEntitySort(pageable.getSort()));
        return PagedResponse.from(employeeRepository
                .findAll(EmployeeSpecifications.matching(criteria), entityPageable)
                .map(EmployeeSummaryResponse::from));
    }

    private static Sort toEntitySort(Sort requested) {
        Sort primary = requested.isSorted()
                ? Sort.by(requested.stream().map(EmployeeService::toEntityOrder).toList())
                : DEFAULT_SORT;
        return primary.and(STABLE_TIEBREAKER);
    }

    private static Sort.Order toEntityOrder(Sort.Order order) {
        String property = Optional.ofNullable(SORTABLE_PROPERTIES.get(order.getProperty()))
                .orElseThrow(() -> new IllegalArgumentException("Unsupported sort property: " + order.getProperty()));
        return new Sort.Order(order.getDirection(), property);
    }

    public EmployeeDetailResponse getById(Long id) {
        Employee employee = findByIdOrThrow(id);
        String managerName = findManagerName(employee.getManagerId());
        return EmployeeDetailResponse.from(employee, managerName);
    }

    public List<EmployeeSalaryProjection> findAllForReporting() {
        return employeeRepository.findAllProjections();
    }

    @Transactional
    public EmployeeDetailResponse updateProfile(Long id, EmployeeProfileUpdateRequest request) {
        Employee employee = findByIdOrThrow(id);
        Long managerId = request.managerId();
        if (managerId != null && !employeeRepository.existsById(managerId)) {
            throw new InvalidReferenceException("managerId", "Employee " + managerId + " was not found");
        }
        employee.applyProfileUpdate(
                request.department(),
                request.jobLevel(),
                CountryCode.of(request.countryCode()),
                request.hireDate(),
                managerId,
                clock.instant());
        employeeRepository.save(employee);
        return EmployeeDetailResponse.from(employee, findManagerName(employee.getManagerId()));
    }

    private Employee findByIdOrThrow(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Employee", id));
    }

    private String findManagerName(Long managerId) {
        return Optional.ofNullable(managerId)
                .flatMap(employeeRepository::findById)
                .map(Employee::getName)
                .orElse(null);
    }
}
