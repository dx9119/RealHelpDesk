package com.ukhanov.realhelpdesk.feature.messagemanager.controller;

import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageRequest;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageResponse;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.MessageResponse;
import com.ukhanov.realhelpdesk.feature.messagemanager.exception.MessageException;
import com.ukhanov.realhelpdesk.feature.messagemanager.service.MessageManageService;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.util.List;

@RestController

@RequestMapping("/api/v1/message/")
public class MessageController {

    private final MessageManageService messageManageService;

    public MessageController(MessageManageService messageManageService) {
        this.messageManageService = messageManageService;
    }


    @PostMapping("{portalId}/{ticketId}")
    @PreAuthorize("@ticketAccessValidationService.hasTicketAccess(#portalId, #ticketId)")
    public ResponseEntity<CreateMessageResponse> createMessage(@Valid
                                                               @RequestBody CreateMessageRequest request,
                                                               @PathVariable Long ticketId,
                                                               @PathVariable Long portalId)
            throws MessageException, MessagingException, TicketException, UnsupportedEncodingException {
        CreateMessageResponse response = messageManageService.createMessage(request, ticketId, portalId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("{portalId}/{ticketId}")
    @PreAuthorize("@ticketAccessValidationService.hasTicketAccess(#portalId, #ticketId)")
    public ResponseEntity<List<MessageResponse>> getAllMessages(@PathVariable Long ticketId,
                                                                @PathVariable Long portalId) //PreAuthorize
        throws MessageException, PortalException {
        List<MessageResponse> response = messageManageService.getAllMessage(ticketId);
        return ResponseEntity.ok(response);
    }

}
