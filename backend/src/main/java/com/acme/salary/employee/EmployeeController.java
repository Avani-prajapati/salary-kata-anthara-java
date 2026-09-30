package com.acme.salary.employee;

import com.acme.salary.common.paging.PagedResponse;
import com.acme.salary.employee.dto.EmployeeDetailResponse;
import com.acme.salary.employee.dto.EmployeeSearchCriteria;
import com.acme.salary.employee.dto.EmployeeSummaryResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public PagedResponse<EmployeeSummaryResponse> search(
            @RequestParam Optional<String> name,
            @RequestParam Optional<Department> department,
            @RequestParam Optional<String> countryCode,
            @RequestParam Optional<JobLevel> jobLevel,
            Pageable pageable) {
        EmployeeSearchCriteria criteria = new EmployeeSearchCriteria(
                name.map(String::trim).filter(fragment -> !fragment.isEmpty()),
                department,
                countryCode.map(CountryCode::of).map(CountryCode::value),
                jobLevel);
        return employeeService.search(criteria, pageable);
    }

    @GetMapping("/{id}")
    public EmployeeDetailResponse getById(@PathVariable Long id) {
        return employeeService.getById(id);
    }
}
