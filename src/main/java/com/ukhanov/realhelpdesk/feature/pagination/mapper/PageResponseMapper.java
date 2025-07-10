package com.ukhanov.realhelpdesk.feature.pagination.mapper;


import com.ukhanov.realhelpdesk.feature.pagination.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class PageResponseMapper {

  public <T> PageResponse<T> map(Page<T> page) {
    return new PageResponse.Builder<T>()
        .content(page.getContent())
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .last(page.isLast())
        .build();
  }
}
