package com.ukhanov.realhelpdesk.feature.messagemanager.service;

import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.message.model.MessageModel;
import com.ukhanov.realhelpdesk.domain.message.service.MessageDomainService;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.service.TicketDomainService;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageRequest;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageResponse;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.MessageResponse;
import com.ukhanov.realhelpdesk.feature.messagemanager.mapper.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class MessageManageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageManageService.class);
    private final MessageMapper messageMapper;
    private final CurrentUserProvider currentUserProvider;
    private final TicketDomainService ticketDomainService;
    private final MessageDomainService messageDomainService;

    public MessageManageService(MessageMapper messageMapper, CurrentUserProvider currentUserProvider, TicketDomainService ticketDomainService, MessageDomainService messageDomainService) {
        this.messageMapper = messageMapper;
        this.currentUserProvider = currentUserProvider;
        this.ticketDomainService = ticketDomainService;
        this.messageDomainService = messageDomainService;
    }

    public CreateMessageResponse createMessage(CreateMessageRequest request, Long ticketId) {
        Objects.requireNonNull(request, "CreateMessageRequest must not be null");
        Objects.requireNonNull(ticketId, "ticketId must not be null");

        UserModel user = currentUserProvider.getCurrentUserModel();
        TicketModel ticket = ticketDomainService.findTicketById(ticketId);

        MessageModel message = messageDomainService.saveMessage(messageMapper.toEntity(request, user, ticket));

        logger.info("Message save for ticket ID: {}", ticketId);
        return new CreateMessageResponse("Message created, id: " + message.getId());
    }

    public List<MessageResponse> getAllMessage(Long ticketId) {
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        logger.debug("Getting all messages for ticket ID: {}", ticketId);

        List<MessageModel> messages = messageDomainService.getMessagesByTicketId(ticketId);
        logger.info("Found {} messages for ticket ID: {}", messages.size(), ticketId);

        return messages.stream()
                .map(messageMapper::toResponse)
                .toList();
    }

}
