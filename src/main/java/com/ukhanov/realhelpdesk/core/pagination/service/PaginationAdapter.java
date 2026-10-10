package com.ukhanov.realhelpdesk.core.pagination.service;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.core.pagination.exception.PaginationException;

@Service
public class PaginationAdapter {

    private static final Logger logger = LoggerFactory.getLogger(PaginationAdapter.class);

    public <T> PageResponse<T> mapToResponse(Page<T> page, String sortBy, String order) {
        return mapToResponse(page);
    }

    public <T> PageResponse<T> mapToResponse(Page<T> page) {
        Objects.requireNonNull(page, "Защита от null: Page необходим для маппинга");

        PageResponse<T> response = new PageResponse<>();
        response.setContent(page.getContent());
        response.setPage(page.getNumber());
        response.setSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        response.setLast(page.isLast());
        return response;
    }

    public PageRequest buildPageRequest(int page, int size, String sortBy, String order, Set<String> allowedSortFields) {
        Objects.requireNonNull(sortBy, "Параметр sortBy не должен быть null");
        Objects.requireNonNull(order, "Параметр order не должен быть null");
        Objects.requireNonNull(allowedSortFields, "allowedSortFields не должен быть null");

        if (!allowedSortFields.contains(sortBy)) {
            throw new PaginationException("Недопустимое поле сортировки \"" + sortBy + "\". Допустимые поля: "
                    + String.join(", ", new TreeSet<>(allowedSortFields)));
        }

        Sort sort = resolveSortDirection(sortBy, order);
        return PageRequest.of(page, size, sort);
    }

    private Sort resolveSortDirection(String sortBy, String order) {
        return "desc".equalsIgnoreCase(order) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
    }
}
