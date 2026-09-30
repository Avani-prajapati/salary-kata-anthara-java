package com.acme.salary.compensation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalaryHistoryRepository extends JpaRepository<SalaryHistory, Long> {

    List<SalaryHistory> findByEmployeeIdOrderByEffectiveDateDesc(Long employeeId);

    Optional<SalaryHistory> findTopByEmployeeIdOrderByEffectiveDateDesc(Long employeeId);
}
