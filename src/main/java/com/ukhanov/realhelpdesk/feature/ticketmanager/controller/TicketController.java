package com.ukhanov.realhelpdesk.feature.ticketmanager.controller;

import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
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
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
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
    @PreAuthorize("@accessValidationService.hasPortalAccess(#portalId)")
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
        return ticketManageService.getIdTicketNoAnswer(portalId);
    }

    @GetMapping("/{portalId}/ticket/status/{status}")
    @PreAuthorize("@accessValidationService.hasPortalAccess(#portalId)")
    public Set<Long> getTicketsWithStatus(
        @PathVariable Long portalId,
        @PathVariable TicketStatus status
    ) throws TicketException, PortalException {
        return ticketManageService.getIdTicketWithStatus(portalId, status);
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

    @GetMapping("{portalId}/ticket/page/status/{status}")
    public PageResponse<TicketResponse> getPagedTicketsByStatus(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String order,
        @PathVariable Long portalId,
        @PathVariable TicketStatus status
    ) throws TicketException, PortalException {
        Set<Long> ids = ticketManageService.getIdTicketWithStatus(portalId, status);
        return ticketManageService.getPageTicketsByIds(ids, page, size, sortBy, order);
    }

    @GetMapping("/ticket/search")
    public PageResponse<TicketResponse> searchTickets(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String order,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
        @RequestParam(required = false) TicketStatus ticketStatus,
        @RequestParam(required = false) TicketPriority ticketPriority,
        @RequestParam(required = false) boolean isMyTickets
    ) throws TicketException, PortalException {
        return ticketManageService.getPageTicketsByFilters(
            page, size, sortBy, order, search, startDate, endDate, ticketStatus, ticketPriority, isMyTickets
        );
    }



    @GetMapping("/{portalId}/ticket/{ticketId}")
    @PreAuthorize("@ticketAccessValidationService.hasTicketAccess(#portalId, #ticketId)")
    public TicketResponse getTicketById(@PathVariable Long portalId, @PathVariable Long ticketId) throws TicketException {
        return ticketManageService.getTicketById(ticketId);
    }


    @PostMapping("/{portalId}/ticket/set/status/{ticketId}")
    @PreAuthorize("@accessValidationService.hasPortalAccess(#portalId)")
    public ResponseEntity<String> setStatusTicket(
        @PathVariable Long portalId,
        @PathVariable Long ticketId,
        @RequestParam TicketStatus status
    ) throws TicketException, PortalException, MessagingException {
        ticketManageService.setTicketStatus(portalId, ticketId, status);
        return ResponseEntity.ok("success");
    }

    @PostMapping("/{portalId}/ticket/set/priority/{ticketId}")
    @PreAuthorize("@accessValidationService.hasPortalAccess(#portalId)")
    public ResponseEntity<String> setPriorityTicket(
        @PathVariable Long portalId,
        @PathVariable Long ticketId,
        @RequestParam TicketPriority priority
    ) throws TicketException, PortalException, MessagingException {
        ticketManageService.setTicketPriority(portalId, ticketId, priority);
        return ResponseEntity.ok("success");
    }

}