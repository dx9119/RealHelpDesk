package com.ukhanov.realhelpdesk.feature.ticketmanager.dto;
import java.time.Instant;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;

public record TicketResponse(Long id, String title, String authorFullName, String portalName, Instant createdAt, Long portalId) {

    /** Фасад для сервисов, которым достаточно TicketModel: неудобные null-проверки связей живут здесь, а не в вызывающем коде. */
    public TicketResponse(TicketModel ticket) {
        this(ticket.getId(), ticket.getTitle(), ticket.getAuthor() != null ? ticket.getAuthor().getFirstName() : null,
                ticket.getPortal() != null ? ticket.getPortal().getName() : null, ticket.getCreatedAt(),
                ticket.getPortal() != null ? ticket.getPortal().getId() : null);
    }
}
