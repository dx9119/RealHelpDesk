package com.ukhanov.RealHelpDesk.feature.ticketmanager.mapper;

import com.ukhanov.RealHelpDesk.core.security.user.model.UserModel;
import com.ukhanov.RealHelpDesk.domain.portal.model.PortalModel;
import com.ukhanov.RealHelpDesk.domain.ticket.model.TicketModel;
import com.ukhanov.RealHelpDesk.domain.ticket.model.TicketPriority;
import com.ukhanov.RealHelpDesk.domain.ticket.model.TicketStatus;
import com.ukhanov.RealHelpDesk.feature.ticketmanager.dto.CreateTicketRequest;
import com.ukhanov.RealHelpDesk.feature.ticketmanager.dto.TicketResponse;

import java.util.Objects;

public class TicketMapper {

    public static TicketModel fromRequest(CreateTicketRequest request, UserModel author, PortalModel portal) {
        TicketModel ticket = new TicketModel();
        ticket.setTitle(request.getTitle());
        ticket.setBody(request.getBody());
        ticket.setAuthor(author);
        ticket.setAssignedUser(author);
        ticket.setPortal(portal);
        ticket.setTicketPriority(request.getTicketPriority() != null ? request.getTicketPriority() : TicketPriority.NONE);
        ticket.setTicketStatus(TicketStatus.OPEN); // статус по умолчанию
        return ticket;
    }

    public static TicketResponse toResponse(TicketModel model) {
        Objects.requireNonNull(model, "TicketModel must not be null");

        String authorName = model.getAuthor() != null
                ? model.getAuthor().getLastName() + " " + model.getAuthor().getFirstName()
                : "Неизвестный автор";

        String assignedUserName = model.getAssignedUser() != null
                ? model.getAssignedUser().getLastName() + " " + model.getAssignedUser().getFirstName()
                : null;

        String portalName = model.getPortal() != null
                ? model.getPortal().getName()
                : null;

        return new TicketResponse.Builder()
                .id(model.getId())
                .title(model.getTitle())
                .body(model.getBody())
                .createdAt(model.getCreatedAt())
                .ticketPriority(model.getTicketPriority())
                .ticketStatus(model.getTicketStatus())
                .authorFullName(authorName)
                .assignedUserFullName(assignedUserName)
                .portalName(portalName)
                .build();
    }

}
