package com.ukhanov.realhelpdesk.feature.messagemanager.service;

import com.ukhanov.realhelpdesk.core.mail.model.EmailTemplates;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.message.model.MessageModel;
import com.ukhanov.realhelpdesk.domain.message.service.MessageDomainService;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import com.ukhanov.realhelpdesk.domain.ticket.service.TicketDomainService;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageRequest;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageResponse;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.MessageResponse;
import com.ukhanov.realhelpdesk.feature.messagemanager.exception.MessageException;
import com.ukhanov.realhelpdesk.feature.messagemanager.mapper.MessageMapper;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Objects;

@Service
public class MessageManageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageManageService.class);
    private final MessageMapper messageMapper;
    private final CurrentUserProvider currentUserProvider;
    private final TicketDomainService ticketDomainService;
    private final MessageDomainService messageDomainService;
    private final EmailDeliveryService emailDeliveryService;

    public MessageManageService(
            MessageMapper messageMapper,
            CurrentUserProvider currentUserProvider,
            TicketDomainService ticketDomainService,
            MessageDomainService messageDomainService,
            EmailDeliveryService emailDeliveryService) {
        this.messageMapper = messageMapper;
        this.currentUserProvider = currentUserProvider;
        this.ticketDomainService = ticketDomainService;
        this.messageDomainService = messageDomainService;
        this.emailDeliveryService = emailDeliveryService;
    }

    public CreateMessageResponse createMessage(CreateMessageRequest request, Long ticketId, Long portalId)
            throws MessagingException, TicketException, UnsupportedEncodingException {
        Objects.requireNonNull(request, "Запрос на создание сообщения не должен быть null");
        Objects.requireNonNull(ticketId, "ID заявки не должен быть null");

        UserModel user = currentUserProvider.getCurrentUserModel();
        TicketModel ticket = ticketDomainService.findTicketById(ticketId);
        ticket.setTicketStatus(TicketStatus.IN_PROGRESS);
        ticketDomainService.saveTicket(ticket);
        MessageModel message = messageDomainService.saveMessage(messageMapper.toEntity(request, user, ticket));

        // Отправляем письмо с оповещением о новом сообщении в тикете
        emailDeliveryService.sendUserNotification(
                ticket.getAuthor().getEmail(),
                EmailTemplates.ticketReplySubject(ticket.getId()),
                EmailTemplates.ticketReplyBody(ticket.getId(), portalId),
                NotificationEvent.NEW_MESSAGE
        );

        logger.info("Сообщение сохранено для заявки с ID: {}", ticketId);
        return new CreateMessageResponse("Сообщение создано, ID: " + message.getId());
    }

    public List<MessageResponse> getAllMessage(Long ticketId)
            throws MessageException, PortalException {
        Objects.requireNonNull(ticketId, "ID заявки не должен быть null");

        List<MessageModel> messages = messageDomainService.getMessagesByTicketId(ticketId);
        logger.info("Найдено {} сообщений для заявки с ID: {}", messages.size(), ticketId);

        return messages.stream()
                .map(messageMapper::toResponse)
                .toList();
    }

}
