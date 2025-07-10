package com.ukhanov.realhelpdesk.feature.messagemanager.controller;

import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageRequest;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageResponse;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.MessageResponse;
import com.ukhanov.realhelpdesk.feature.messagemanager.exception.MessageException;
import com.ukhanov.realhelpdesk.feature.messagemanager.service.MessageManageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/message/{portalId}/{ticketId}")
public class MessageController {

    private final MessageManageService messageManageService;

    public MessageController(MessageManageService messageManageService) {
        this.messageManageService = messageManageService;
    }

    @PostMapping
    public ResponseEntity<CreateMessageResponse> createMessage(@Valid
                                                               @RequestBody CreateMessageRequest request,
                                                               @PathVariable Long ticketId) throws MessageException {
        CreateMessageResponse response = messageManageService.createMessage(request, ticketId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<MessageResponse>> getAllMessages(@PathVariable Long ticketId,
                                                                @PathVariable Long portalId) throws MessageException {
        List<MessageResponse> response = messageManageService.getAllMessage(ticketId,portalId);
        return ResponseEntity.ok(response);
    }


}
