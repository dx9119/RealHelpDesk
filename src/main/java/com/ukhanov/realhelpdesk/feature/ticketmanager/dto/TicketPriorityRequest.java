package com.ukhanov.realhelpdesk.feature.ticketmanager.dto;

import jakarta.validation.constraints.NotNull;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;

public class TicketPriorityRequest {

    @NotNull(message = "Приоритет заявки обязателен")
    private TicketPriority priority;

    public TicketPriorityRequest() {
    }

    public TicketPriorityRequest(TicketPriority priority) {
        this.priority = priority;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }
}
