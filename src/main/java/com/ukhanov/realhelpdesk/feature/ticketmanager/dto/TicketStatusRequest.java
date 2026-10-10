package com.ukhanov.realhelpdesk.feature.ticketmanager.dto;

import jakarta.validation.constraints.NotNull;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;

public record TicketStatusRequest(@NotNull(message = "Статус заявки обязателен") TicketStatus status) {
}
