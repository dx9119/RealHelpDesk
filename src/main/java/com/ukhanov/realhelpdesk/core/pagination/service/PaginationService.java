package com.ukhanov.realhelpdesk.core.pagination.service;


import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.core.pagination.mapper.PageResponseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class PaginationService {

  private static final Logger logger = LoggerFactory.getLogger(PaginationService.class);

  private final PageResponseMapper pageResponseMapper;

  public PaginationService(PageResponseMapper pageResponseMapper,
      PageResponseMapper pageResponseMapper1) {

    this.pageResponseMapper = pageResponseMapper1;
  }

  public <T> PageResponse<T> mapToResponse(Page<T> page, String sortBy, String order) {
    Objects.requireNonNull(page, "Защита от null: Page необходим для маппинга");
    return pageResponseMapper.map(page);
  }

  public PageRequest buildPageRequest(int page, int size, String sortBy, String order) {
    Objects.requireNonNull(sortBy, "Параметр sortBy не должен быть null");
    Objects.requireNonNull(order, "Параметр order не должен быть null");

    Sort sort = resolveSortDirection(sortBy, order);
    return PageRequest.of(page, size, sort);
  }

  private Sort resolveSortDirection(String sortBy, String order) {
    return "desc".equalsIgnoreCase(order)
        ? Sort.by(sortBy).descending()
        : Sort.by(sortBy).ascending();
  }
}
