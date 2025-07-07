package com.ukhanov.realhelpdesk.feature.messagemanager.mapper;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.message.model.MessageModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageRequest;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.MessageResponse;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class MessageMapper {

    public MessageModel toEntity(CreateMessageRequest request, UserModel author, TicketModel ticket) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(author, "author must not be null");
        Objects.requireNonNull(ticket, "ticket must not be null");

        MessageModel message = new MessageModel();
        message.setMessageText(request.getMessageText());
        message.setAuthor(author);
        message.setTicket(ticket);
        return message;
    }

    public MessageResponse toResponse(MessageModel model) {
        Objects.requireNonNull(model, "MessageModel must not be null");

        MessageResponse response = new MessageResponse();
        response.setId(model.getId());
        response.setMessageText(model.getMessageText());
        response.setCreatedAt(model.getCreatedAt());
        response.setTicketId(model.getTicket() != null ? model.getTicket().getId() : null);
        response.setAuthorFullName(model.getAuthor() != null
                ? model.getAuthor().getLastName() + " " + model.getAuthor().getFirstName()
                : "Неизвестный автор");
        return response;
    }

}
