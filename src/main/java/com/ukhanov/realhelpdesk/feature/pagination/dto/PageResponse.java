package com.ukhanov.realhelpdesk.feature.pagination.dto;

import java.util.List;

public class PageResponse<T> {

  private List<T> content;
  private int page;
  private int size;
  private long totalElements;
  private int totalPages;
  private boolean last;

  public List<T> getContent() {
    return content;
  }

  public void setContent(List<T> content) {
    this.content = content;
  }

  public int getPage() {
    return page;
  }

  public void setPage(int page) {
    this.page = page;
  }

  public int getSize() {
    return size;
  }

  public void setSize(int size) {
    this.size = size;
  }

  public long getTotalElements() {
    return totalElements;
  }

  public void setTotalElements(long totalElements) {
    this.totalElements = totalElements;
  }

  public int getTotalPages() {
    return totalPages;
  }

  public void setTotalPages(int totalPages) {
    this.totalPages = totalPages;
  }

  public boolean isLast() {
    return last;
  }

  public void setLast(boolean last) {
    this.last = last;
  }

  public static class Builder<T> {
    private final PageResponse<T> response = new PageResponse<>();

    public Builder<T> content(List<T> content) {
      response.content = content;
      return this;
    }

    public Builder<T> page(int page) {
      response.page = page;
      return this;
    }

    public Builder<T> size(int size) {
      response.size = size;
      return this;
    }

    public Builder<T> totalElements(long totalElements) {
      response.totalElements = totalElements;
      return this;
    }

    public Builder<T> totalPages(int totalPages) {
      response.totalPages = totalPages;
      return this;
    }

    public Builder<T> last(boolean last) {
      response.last = last;
      return this;
    }

    public PageResponse<T> build() {
      return response;
    }
  }
}

