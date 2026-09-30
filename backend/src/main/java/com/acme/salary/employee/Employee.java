package com.acme.salary.employee;

import com.acme.salary.common.money.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private Department department;

    @Enumerated(EnumType.STRING)
    private JobLevel jobLevel;

    private String countryCode;

    private LocalDate hireDate;

    private Long managerId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "salary_amount", nullable = false)),
            @AttributeOverride(name = "currencyCode", column = @Column(name = "salary_currency", nullable = false))
    })
    private Money currentSalary;

    private Instant createdAt;

    private Instant updatedAt;

    protected Employee() {
    }

    public Employee(
            String name,
            Department department,
            JobLevel jobLevel,
            CountryCode country,
            LocalDate hireDate,
            Long managerId,
            Money currentSalary,
            Instant createdAt) {
        this.name = name;
        this.department = department;
        this.jobLevel = jobLevel;
        this.countryCode = country.value();
        this.hireDate = hireDate;
        this.managerId = managerId;
        this.currentSalary = currentSalary;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Department getDepartment() {
        return department;
    }

    public JobLevel getJobLevel() {
        return jobLevel;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public Long getManagerId() {
        return managerId;
    }

    public Money getCurrentSalary() {
        return currentSalary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void recordSalaryChange(Money currentSalary, Instant updatedAt) {
        this.currentSalary = currentSalary;
        this.updatedAt = updatedAt;
    }

    public void applyProfileUpdate(
            Department department,
            JobLevel jobLevel,
            CountryCode country,
            LocalDate hireDate,
            Long managerId,
            Instant updatedAt) {
        this.department = department;
        this.jobLevel = jobLevel;
        this.countryCode = country.value();
        this.hireDate = hireDate;
        this.managerId = managerId;
        this.updatedAt = updatedAt;
    }
}
