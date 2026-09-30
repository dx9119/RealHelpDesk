package com.ukhanov.realhelpdesk.feature.ticketmanager.dto;

import jakarta.validation.constraints.NotNull;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;

public class TicketStatusRequest {

    @NotNull(message = "Статус заявки обязателен")
    private TicketStatus status;

    public TicketStatusRequest() {
    }

    public TicketStatusRequest(TicketStatus status) {
        this.status = status;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }
}
