package com.ukhanov.realhelpdesk.feature.ticketmanager.controller;

import com.ukhanov.realhelpdesk.feature.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketRequest;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.service.TicketManageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portals/{portalId}/ticket")
public class TicketController {

    private static final Logger logger = LoggerFactory.getLogger(TicketController.class);
    private final TicketManageService ticketManageService;

    public TicketController(TicketManageService ticketManageService) {
        this.ticketManageService = ticketManageService;
    }

    @PostMapping
    public ResponseEntity<CreateTicketResponse> createTicketForPortal(@Valid
                                                       @RequestBody CreateTicketRequest request,
                                                       @PathVariable Long portalId) throws TicketException {
        CreateTicketResponse response = ticketManageService.createTicket(request, portalId);
        return ResponseEntity.ok(response);
    }


    @GetMapping
    public PageResponse<TicketResponse> getPagedTicketsByPortalId(
        @PathVariable Long portalId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String order
    ) throws TicketException {
        return ticketManageService.getPageTickets(portalId, page, size, sortBy, order);
    }

}