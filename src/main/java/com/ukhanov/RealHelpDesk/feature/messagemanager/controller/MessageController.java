package com.ukhanov.RealHelpDesk.feature.messagemanager.controller;

import com.ukhanov.RealHelpDesk.feature.messagemanager.dto.CreateMessageRequest;
import com.ukhanov.RealHelpDesk.feature.messagemanager.dto.CreateMessageResponse;
import com.ukhanov.RealHelpDesk.feature.messagemanager.dto.MessageResponse;
import com.ukhanov.RealHelpDesk.feature.messagemanager.exception.MessageException;
import com.ukhanov.RealHelpDesk.feature.messagemanager.service.MessageManageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/message/{ticketId}")
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
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping
    public ResponseEntity<List<MessageResponse>> getAllMessages(@PathVariable Long ticketId) throws MessageException {
        List<MessageResponse> responses = messageManageService.getAllMessage(ticketId);
        return ResponseEntity.status(HttpStatus.OK).body(responses);
    }


}
