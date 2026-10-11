package com.ukhanov.realhelpdesk.feature.ticketmanager.controller;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.service.TicketSearchService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@Validated
@RequestMapping("/api/v1/tickets")
public class TicketSearchController {

    private final TicketSearchService ticketSearchService;

    /**
     * Единый поиск по заявкам; {@code mine=true} заменяет прежний дубль {@code GET /tickets/mine} — у него был свой DTO и свой стиль
     * пагинации.
     */
    @GetMapping
    public ResponseEntity<PageResponse<TicketResponse>> searchTickets(
            @RequestParam(required = false) @Size(max = 50, message = "Строка поиска не может превышать 50 символов") String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) TicketStatus status, @RequestParam(required = false) TicketPriority priority,
            @RequestParam(defaultValue = "false") boolean mine, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size, @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String order) {

        PageResponse<TicketResponse> results = ticketSearchService.searchTickets(search, startDate, endDate, status, priority, mine, page,
                size, sortBy, order);

        return ResponseEntity.ok(results);
    }
}
