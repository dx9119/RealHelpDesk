package com.ukhanov.realhelpdesk.feature.ticketmanager.mapper;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketAccessStatus;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketRequest;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponseOld;

import java.util.Objects;

public class TicketMapper {

    public static TicketModel fromRequest(CreateTicketRequest request, UserModel author, PortalModel portal) {
        TicketModel ticket = new TicketModel();
        ticket.setTitle(request.getTitle());
        ticket.setBody(request.getBody());
        ticket.setAuthor(author);
        ticket.setPortal(portal);
        ticket.setTicketPriority(request.getTicketPriority() != null ? request.getTicketPriority() : TicketPriority.NONE);
        ticket.setTicketStatus(TicketStatus.OPEN);
        ticket.setAccessStatus(request.getTicketAccessStatus() != null ? request.getTicketAccessStatus() : TicketAccessStatus.CREATOR_AND_PORTAL_USERS);
        return ticket;
    }

    public static TicketResponseOld toResponse(TicketModel model) {
        Objects.requireNonNull(model, "TicketModel не должен быть null");

        String authorName = model.getAuthor() != null
                ? model.getAuthor().getLastName() + " " + model.getAuthor().getFirstName()
                : "Неизвестный автор";

        String portalName = model.getPortal() != null
                ? model.getPortal().getName()
                : null;

        return new TicketResponseOld.Builder()
                .id(model.getId())
                .title(model.getTitle())
                .body(model.getBody())
                .createdAt(model.getCreatedAt())
                .ticketPriority(model.getTicketPriority())
                .ticketStatus(model.getTicketStatus())
                .authorFullName(authorName)
                .portalName(portalName)
                .portalId(model.getPortal().getId())
                .ticketAccessStatus(model.getAccessStatus())
                .build();
    }

}
