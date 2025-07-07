package com.ukhanov.realhelpdesk.feature.ticketmanager.controller;

import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketRequest;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.service.TicketManageService;
import jakarta.validation.Valid;
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
    public ResponseEntity<CreateTicketResponse> create(@Valid
                                                       @RequestBody CreateTicketRequest request,
                                                       @PathVariable Long portalId) throws TicketException {
        CreateTicketResponse response = ticketManageService.createTicket(request, portalId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }


    @GetMapping
    public ResponseEntity<List<TicketResponse>> readTicket (@Valid @PathVariable Long portalId) throws TicketException {
        List<TicketResponse> response = ticketManageService.getAllTickets(portalId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

}





