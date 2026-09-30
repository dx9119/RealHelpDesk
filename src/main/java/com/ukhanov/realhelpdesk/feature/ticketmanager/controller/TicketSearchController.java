package com.ukhanov.realhelpdesk.feature.ticketmanager.controller;

import java.time.Instant;

import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.core.pagination.service.PaginationAdapter;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponseOld;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.service.TicketManageService;
import com.ukhanov.realhelpdesk.feature.ticketmanager.service.TicketSearchService;

@RestController
@Validated
@RequestMapping("/api/v1/tickets")
public class TicketSearchController {

    private final TicketSearchService ticketSearchService;
    private final TicketManageService ticketManageService;
    private final PaginationAdapter paginationAdapter;

    public TicketSearchController(TicketSearchService ticketSearchService, TicketManageService ticketManageService,
            PaginationAdapter paginationAdapter) {
        this.ticketSearchService = ticketSearchService;
        this.ticketManageService = ticketManageService;
        this.paginationAdapter = paginationAdapter;
    }

    @GetMapping
    public ResponseEntity<PageResponse<TicketResponse>> searchTickets(
            @RequestParam(required = false) @Size(max = 50, message = "Строка поиска не может превышать 50 символов") String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) TicketStatus status, @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false, defaultValue = "false") boolean mine, Pageable pageable) {

        Page<TicketResponse> results = ticketSearchService.searchTickets(search, startDate, endDate, status, priority, mine, pageable);

        return ResponseEntity.ok(paginationAdapter.mapToResponse(results));
    }

    @GetMapping("/mine")
    public PageResponse<TicketResponseOld> getMyTickets(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String order) throws TicketException, PortalException {
        return ticketManageService.getPageTicketsByAutor(page, size, sortBy, order);
    }
}
