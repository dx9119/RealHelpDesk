package com.ukhanov.realhelpdesk.domain.message.service;

import java.util.List;
import java.util.Objects;

import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.domain.message.model.MessageModel;
import com.ukhanov.realhelpdesk.domain.message.repository.MessageRepository;
import com.ukhanov.realhelpdesk.feature.messagemanager.exception.MessageException;

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

    public MessageModel getMessageById(Long messageId) throws MessageException {
        Objects.requireNonNull(messageId, "ID сообщения не должен быть null");

        return messageRepository.findById(messageId).orElseThrow(() -> MessageException.notFound("Сообщение не найдено"));
    }

    public List<MessageModel> getMessagesByTicketId(Long ticketId) {
        Objects.requireNonNull(ticketId, "Идентификатор заявки не должен быть null");

        return messageRepository.findByTicketId(ticketId);
    }

}
