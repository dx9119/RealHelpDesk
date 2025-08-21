package com.ukhanov.realhelpdesk.domain.message.service;

import com.ukhanov.realhelpdesk.domain.message.model.MessageModel;
import com.ukhanov.realhelpdesk.domain.message.repository.MessageRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class MessageDomainService {

    private static final Logger logger = LoggerFactory.getLogger(MessageDomainService.class);

    private final MessageRepository messageRepository;

    public MessageDomainService(MessageRepository messageRepository) {
        this.messageRepository = Objects.requireNonNull(messageRepository, "messageRepository must not be null");
    }

    @Transactional
    public MessageModel saveMessage(MessageModel message) {
        Objects.requireNonNull(message, "Сообщение не должно быть null");

        return messageRepository.save(message);
    }

    public List<MessageModel> getMessagesByTicketId(Long ticketId) {
        Objects.requireNonNull(ticketId, "Идентификатор заявки не должен быть null");

        return messageRepository.findByTicketId(ticketId);
    }

}
