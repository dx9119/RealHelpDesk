package com.ukhanov.RealHelpDesk.feature.ticketmanager.dto;

import com.ukhanov.RealHelpDesk.domain.ticket.model.TicketPriority;

public class CreateTicketRequest {

    private String title;
    private String body;
    private TicketPriority ticketPriority;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public TicketPriority getTicketPriority() {
        return ticketPriority;
    }

    public void setTicketPriority(TicketPriority ticketPriority) {
        this.ticketPriority = ticketPriority;
    }
}
