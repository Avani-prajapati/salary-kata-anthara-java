package com.acme.salary.compensation;

import com.acme.salary.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "salary_history")
public class SalaryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long employeeId;

    private BigDecimal amount;

    @Column(name = "currency")
    private String currencyCode;

    private LocalDate effectiveDate;

    private Instant recordedAt;

    protected SalaryHistory() {
    }

    public SalaryHistory(Long employeeId, Money salary, LocalDate effectiveDate, Instant recordedAt) {
        this.employeeId = employeeId;
        this.amount = salary.amount();
        this.currencyCode = salary.currencyCode();
        this.effectiveDate = effectiveDate;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
