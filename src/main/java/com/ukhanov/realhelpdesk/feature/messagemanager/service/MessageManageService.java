package com.ukhanov.realhelpdesk.feature.messagemanager.service;

import com.ukhanov.realhelpdesk.core.mail.dto.TicketMessageNotificationDto;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.security.accesscontrol.AccessValidationService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.message.model.MessageModel;
import com.ukhanov.realhelpdesk.domain.message.service.MessageDomainService;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.service.TicketDomainService;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageRequest;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageResponse;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.MessageResponse;
import com.ukhanov.realhelpdesk.feature.messagemanager.exception.MessageException;
import com.ukhanov.realhelpdesk.feature.messagemanager.mapper.MessageMapper;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import jakarta.mail.MessagingException;
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
    private final AccessValidationService accessValidationService;
    private final EmailDeliveryService emailDeliveryService;

    public MessageManageService(MessageMapper messageMapper, CurrentUserProvider currentUserProvider, TicketDomainService ticketDomainService, MessageDomainService messageDomainService,
        AccessValidationService accessValidationService, EmailDeliveryService emailDeliveryService) {
        this.messageMapper = messageMapper;
        this.currentUserProvider = currentUserProvider;
        this.ticketDomainService = ticketDomainService;
        this.messageDomainService = messageDomainService;
        this.accessValidationService = accessValidationService;
      this.emailDeliveryService = emailDeliveryService;
    }

    public CreateMessageResponse createMessage(CreateMessageRequest request, Long ticketId)
        throws MessagingException {
        Objects.requireNonNull(request, "CreateMessageRequest must not be null");
        Objects.requireNonNull(ticketId, "ticketId must not be null");

        UserModel user = currentUserProvider.getCurrentUserModel();
        TicketModel ticket = ticketDomainService.findTicketById(ticketId);

        MessageModel message = messageDomainService.saveMessage(messageMapper.toEntity(request, user, ticket));

        // Отправляем письмо с оповещением о новом сообщении в тикете
        TicketMessageNotificationDto notify = TicketMessageNotificationDto.builder()
            .info(ticket.getPortal().getId(),ticket.getId())
            .build();

        emailDeliveryService.sendUserNotification(user.getEmail(), notify.getSubject(),notify.getMessage(), NotificationEvent.NEW_MESSAGE);

        logger.info("Message save for ticket ID: {}", ticketId);
        return new CreateMessageResponse("Message created, id: " + message.getId());
    }

    public List<MessageResponse> getAllMessage(Long ticketId)
        throws MessageException, PortalException {
        Objects.requireNonNull(ticketId, "ticketId must not be null");

        List<MessageModel> messages = messageDomainService.getMessagesByTicketId(ticketId);
        logger.info("Found {} messages for ticket ID: {}", messages.size(), ticketId);

        return messages.stream()
                .map(messageMapper::toResponse)
                .toList();
    }

}
