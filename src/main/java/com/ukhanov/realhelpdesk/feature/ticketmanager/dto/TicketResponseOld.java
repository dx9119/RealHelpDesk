package com.ukhanov.realhelpdesk.feature.ticketmanager.dto;

import java.time.Instant;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketAccessStatus;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;

public record TicketResponseOld(Long id, String title, String body, String authorFullName, String portalName, TicketPriority ticketPriority,
        TicketStatus ticketStatus, Instant createdAt, Long portalId, TicketAccessStatus ticketAccessStatus) {
}
