package com.ukhanov.realhelpdesk.feature.pagination.service;


import com.ukhanov.realhelpdesk.feature.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.feature.pagination.mapper.PageResponseMapper;
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
    logger.debug("Mapping Page to PageResponse — sortBy: {}, order: {}", sortBy, order);
    Objects.requireNonNull(page, "Page object must not be null");
    return pageResponseMapper.map(page);
  }

  public PageRequest buildPageRequest(int page, int size, String sortBy, String order) {
    logger.debug("Creating PageRequest — page: {}, size: {}, sortBy: {}, order: {}", page, size, sortBy, order);

    Objects.requireNonNull(sortBy, "sortBy must not be null");
    Objects.requireNonNull(order, "order must not be null");

    Sort sort = resolveSortDirection(sortBy, order);
    logger.info("PageRequest created with sort: {}", sort);

    return PageRequest.of(page, size, sort);
  }

  private Sort resolveSortDirection(String sortBy, String order) {
    return "desc".equalsIgnoreCase(order)
        ? Sort.by(sortBy).descending()
        : Sort.by(sortBy).ascending();
  }
}
