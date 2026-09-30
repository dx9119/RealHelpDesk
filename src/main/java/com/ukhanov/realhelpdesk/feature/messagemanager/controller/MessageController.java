package com.ukhanov.realhelpdesk.feature.messagemanager.controller;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.List;

import jakarta.mail.MessagingException;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageRequest;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageResponse;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.MessageResponse;
import com.ukhanov.realhelpdesk.feature.messagemanager.exception.MessageException;
import com.ukhanov.realhelpdesk.feature.messagemanager.service.MessageManageService;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;

@RestController
@RequestMapping("/api/v1/portals/{portalId}/tickets/{ticketId}/messages")
public class MessageController {

    private final MessageManageService messageManageService;

    public MessageController(MessageManageService messageManageService) {
        this.messageManageService = messageManageService;
    }

    @PostMapping
    @PreAuthorize("@ticketAccessValidationService.hasTicketAccess(#portalId, #ticketId)")
    public ResponseEntity<CreateMessageResponse> createMessage(@Valid @RequestBody CreateMessageRequest request,
            @PathVariable Long portalId, @PathVariable Long ticketId)
            throws MessageException, MessagingException, TicketException, UnsupportedEncodingException {
        CreateMessageResponse response = messageManageService.createMessage(request, ticketId, portalId);
        return ResponseEntity.created(URI.create("/api/v1/portals/" + portalId + "/tickets/" + ticketId + "/messages/" + response.getId()))
                .body(response);
    }

    @GetMapping
    @PreAuthorize("@ticketAccessValidationService.hasTicketAccess(#portalId, #ticketId)")
    public ResponseEntity<List<MessageResponse>> getAllMessages(@PathVariable Long portalId, @PathVariable Long ticketId)
            throws MessageException, PortalException {
        List<MessageResponse> response = messageManageService.getAllMessage(ticketId);
        return ResponseEntity.ok(response);
    }

}
