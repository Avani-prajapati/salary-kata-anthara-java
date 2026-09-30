package com.acme.salary.employee;

import com.acme.salary.common.paging.PagedResponse;
import com.acme.salary.employee.dto.EmployeeSearchCriteria;
import com.acme.salary.employee.dto.EmployeeSummaryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
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
}
