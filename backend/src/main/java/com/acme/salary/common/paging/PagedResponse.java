package com.acme.salary.common.paging;

import org.springframework.data.domain.Page;

import java.util.List;

public record PagedResponse<T>(List<T> items, int page, int pageSize, long totalElements, int totalPages) {

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
