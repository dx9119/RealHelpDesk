package com.ukhanov.realhelpdesk.feature.ticketmanager.dto;

import jakarta.validation.constraints.NotNull;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;

public record TicketPriorityRequest(@NotNull(message = "Приоритет заявки обязателен") TicketPriority priority) {
}
