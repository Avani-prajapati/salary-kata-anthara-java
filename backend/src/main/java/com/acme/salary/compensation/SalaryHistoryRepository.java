package com.acme.salary.compensation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaryHistoryRepository extends JpaRepository<SalaryHistory, Long> {
}
