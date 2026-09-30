package com.acme.salary.compensation;

import com.acme.salary.compensation.dto.SalaryChangeRequest;
import com.acme.salary.compensation.dto.SalaryHistoryEntryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}")
public class CompensationController {

    private final CompensationService compensationService;

    public CompensationController(CompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @PostMapping("/salary-changes")
    @ResponseStatus(HttpStatus.CREATED)
    public SalaryHistoryEntryResponse changeSalary(
            @PathVariable Long employeeId, @Valid @RequestBody SalaryChangeRequest request) {
        return compensationService.changeSalary(employeeId, request);
    }

    @GetMapping("/salary-history")
    public List<SalaryHistoryEntryResponse> listHistory(@PathVariable Long employeeId) {
        return compensationService.listHistory(employeeId);
    }
}
