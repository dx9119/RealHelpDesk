package com.ukhanov.realhelpdesk.feature.portalmanager.controller;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;

import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.service.TicketSearchService;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/portals")
public class PortalTicketController {

    private final TicketSearchService ticketSearchService;

    public PortalTicketController(TicketSearchService ticketSearchService) {
        this.ticketSearchService = ticketSearchService;
    }

    @GetMapping("/ticket/search")
    public ResponseEntity<Page<TicketResponse>> searchTickets(
            @RequestParam(required = false)
            @Size(max = 50, message = "Строка поиска не может превышать 50 символов")
            String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) TicketStatus ticketStatus,
            @RequestParam(required = false) TicketPriority ticketPriority,
            @RequestParam(required = false, defaultValue = "false") boolean isMyTickets,
            Pageable pageable) {

        Page<TicketResponse> results = ticketSearchService.searchTickets(
                search, startDate, endDate, ticketStatus, ticketPriority, isMyTickets, pageable
        );

        return ResponseEntity.ok(results);
    }
}