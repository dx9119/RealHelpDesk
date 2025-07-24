package com.ukhanov.realhelpdesk.feature.ticketmanager.controller;

import com.ukhanov.realhelpdesk.feature.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketRequest;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.service.TicketManageService;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/portals")
public class TicketController {

    private static final Logger logger = LoggerFactory.getLogger(TicketController.class);
    private final TicketManageService ticketManageService;

    public TicketController(TicketManageService ticketManageService) {
        this.ticketManageService = ticketManageService;
    }

    @PostMapping("/{portalId}/ticket")
    public ResponseEntity<CreateTicketResponse> createTicketForPortal(@Valid
                                                       @RequestBody CreateTicketRequest request,
                                                       @PathVariable Long portalId)
        throws TicketException, PortalException, MessagingException {
        CreateTicketResponse response = ticketManageService.createTicket(request, portalId);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/{portalId}/ticket")
    @PreAuthorize("@accessValidationService.hasPortalAccess(#portalId)")
    public PageResponse<TicketResponse> getPagedTicketsByPortalId(
        @PathVariable Long portalId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String order
    ) throws TicketException, PortalException {
        return ticketManageService.getPageTickets(portalId, page, size, sortBy, order);
    }

    @GetMapping("/{portalId}/ticket/no-answer")
    @PreAuthorize("@accessValidationService.hasPortalAccess(#portalId)")
    public Set<Long> getPagedTicketsByPortalIdNoAnswer(
        @PathVariable Long portalId
    ) throws TicketException, PortalException {
        return ticketManageService.getTicketNoAnswer(portalId);
    }

    @GetMapping("/ticket/author")
    public PageResponse<TicketResponse> getPagedTicketsByAuthor(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String order
    ) throws TicketException, PortalException {
        return ticketManageService.getPageTicketsByAutor(page, size, sortBy, order);
    }


    @GetMapping("/{portalId}/ticket/{ticketId}")
    @PreAuthorize("@accessValidationService.hasPortalAccess(#portalId)")
    public TicketResponse getTicketById(@PathVariable Long portalId, @PathVariable Long ticketId) throws TicketException {
        return ticketManageService.getTicketById(ticketId);
    }



}