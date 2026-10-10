package com.ukhanov.realhelpdesk.feature.messagemanager.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.message.model.MessageModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageRequest;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.MessageResponse;

/** Маппинг сообщений заявки; автор и заявка приходят отдельными параметрами — связь проставляет сервис. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MessageMapper {

    /** Копируются только текст и связи: id, version и createdAt в новой entity проставит JPA, а не запрос. */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "messageText", source = "request.messageText")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "ticket", source = "ticket")
    MessageModel toEntity(CreateMessageRequest request, UserModel author, TicketModel ticket);

    @Mapping(target = "ticketId", source = "ticket.id")
    @Mapping(target = "authorFullName", source = "author", qualifiedByName = "authorFullName")
    MessageResponse toResponse(MessageModel model);

    @Named("authorFullName")
    default String authorFullName(UserModel author) {
        return author != null ? author.getLastName() + " " + author.getFirstName() : "Неизвестный автор";
    }

}
