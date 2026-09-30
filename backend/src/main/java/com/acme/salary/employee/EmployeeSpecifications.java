package com.acme.salary.employee;

import com.acme.salary.employee.dto.EmployeeSearchCriteria;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public final class EmployeeSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private EmployeeSpecifications() {
    }

    public static Specification<Employee> matching(EmployeeSearchCriteria criteria) {
        return Specification.allOf(Stream.of(
                        criteria.name().map(EmployeeSpecifications::nameContains),
                        criteria.department().map(EmployeeSpecifications::hasDepartment),
                        criteria.countryCode().map(EmployeeSpecifications::hasCountry),
                        criteria.jobLevel().map(EmployeeSpecifications::hasJobLevel))
                .flatMap(Optional::stream)
                .toList());
    }

    public static Specification<Employee> nameContains(String fragment) {
        String pattern = "%" + escapeLikeWildcards(fragment.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, builder) -> builder.like(builder.lower(root.get("name")), pattern, LIKE_ESCAPE);
    }

    public static Specification<Employee> hasDepartment(Department department) {
        return (root, query, builder) -> builder.equal(root.get("department"), department);
    }

    public static Specification<Employee> hasCountry(String countryCode) {
        return (root, query, builder) -> builder.equal(root.get("countryCode"), countryCode);
    }

    public static Specification<Employee> hasJobLevel(JobLevel jobLevel) {
        return (root, query, builder) -> builder.equal(root.get("jobLevel"), jobLevel);
    }

    private static String escapeLikeWildcards(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
