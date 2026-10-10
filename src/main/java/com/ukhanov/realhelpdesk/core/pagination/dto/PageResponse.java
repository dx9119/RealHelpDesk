package com.ukhanov.realhelpdesk.core.pagination.dto;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {

    public static class Builder<T> {
        private List<T> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean last;

        public Builder<T> content(List<T> content) {
            this.content = content;
            return this;
        }

        public Builder<T> page(int page) {
            this.page = page;
            return this;
        }

        public Builder<T> size(int size) {
            this.size = size;
            return this;
        }

        public Builder<T> totalElements(long totalElements) {
            this.totalElements = totalElements;
            return this;
        }

        public Builder<T> totalPages(int totalPages) {
            this.totalPages = totalPages;
            return this;
        }

        public Builder<T> last(boolean last) {
            this.last = last;
            return this;
        }

        public PageResponse<T> build() {
            return new PageResponse<>(content, page, size, totalElements, totalPages, last);
        }
    }
}
