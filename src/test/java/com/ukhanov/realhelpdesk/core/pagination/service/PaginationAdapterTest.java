package com.ukhanov.realhelpdesk.core.pagination.service;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.ukhanov.realhelpdesk.core.pagination.exception.PaginationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationAdapterTest {

    private static final Set<String> ALLOWED_FIELDS = Set.of("createdAt", "title", "ticketStatus");

    private final PaginationAdapter adapter = new PaginationAdapter();

    @Test
    void allowedField_appliedToSorting() {
        PageRequest request = adapter.buildPageRequest(1, 20, "title", "desc", ALLOWED_FIELDS);

        assertEquals(1, request.getPageNumber());
        assertEquals(20, request.getPageSize());
        assertNotNull(request.getSort().getOrderFor("title"));
        assertEquals(Sort.Direction.DESC, request.getSort().getOrderFor("title").getDirection());
    }

    @Test
    void ascOrder_whenDescNotSpecified() {
        PageRequest request = adapter.buildPageRequest(0, 10, "createdAt", "asc", ALLOWED_FIELDS);

        assertEquals(Sort.Direction.ASC, request.getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    void unknownField_throwsPaginationException() {
        PaginationException ex = assertThrows(PaginationException.class,
                () -> adapter.buildPageRequest(0, 10, "constructor", "asc", ALLOWED_FIELDS));

        assertTrue(ex.getMessage().contains("\"constructor\""));
        assertTrue(ex.getMessage().contains("createdAt"));
        assertTrue(ex.getMessage().contains("ticketStatus"));
    }

    @Test
    void nestedPath_notPassed() {
        assertThrows(PaginationException.class, () -> adapter.buildPageRequest(0, 10, "author.createdAt", "desc", ALLOWED_FIELDS));
    }

    @Test
    void sqlInSortField_rejected() {
        assertThrows(PaginationException.class, () -> adapter.buildPageRequest(0, 10, "title; DROP TABLE tickets", "asc", ALLOWED_FIELDS));
    }
}
