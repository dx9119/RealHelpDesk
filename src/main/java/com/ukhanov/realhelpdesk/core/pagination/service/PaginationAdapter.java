package com.ukhanov.realhelpdesk.core.pagination.service;


import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.core.pagination.exception.PaginationException;
import com.ukhanov.realhelpdesk.core.pagination.mapper.PageResponseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
public class PaginationAdapter {

  private static final Logger logger = LoggerFactory.getLogger(PaginationAdapter.class);

  private final PageResponseMapper pageResponseMapper;

  public PaginationAdapter(PageResponseMapper pageResponseMapper) {

    this.pageResponseMapper = pageResponseMapper;
  }

  public <T> PageResponse<T> mapToResponse(Page<T> page, String sortBy, String order) {
    Objects.requireNonNull(page, "Защита от null: Page необходим для маппинга");
    return pageResponseMapper.map(page);
  }

  public PageRequest buildPageRequest(int page, int size, String sortBy, String order, Set<String> allowedSortFields) {
    Objects.requireNonNull(sortBy, "Параметр sortBy не должен быть null");
    Objects.requireNonNull(order, "Параметр order не должен быть null");
    Objects.requireNonNull(allowedSortFields, "allowedSortFields не должен быть null");

    if (!allowedSortFields.contains(sortBy)) {
      throw new PaginationException(
          "Недопустимое поле сортировки \"" + sortBy + "\". Допустимые поля: "
              + String.join(", ", new TreeSet<>(allowedSortFields)));
    }

    Sort sort = resolveSortDirection(sortBy, order);
    return PageRequest.of(page, size, sort);
  }

  private Sort resolveSortDirection(String sortBy, String order) {
    return "desc".equalsIgnoreCase(order)
        ? Sort.by(sortBy).descending()
        : Sort.by(sortBy).ascending();
  }
}
