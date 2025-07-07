package com.ukhanov.realhelpdesk.feature.ticketmanager.dto;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateTicketRequest {

    @NotBlank(message = "Тема заявки не может быть пустой")
    @Size(max = 255, message = "Тема заявки не может превышать 255 символов")
    private String title;

    @Size(max = 8000, message = "Сообщение не может быть больше 8000 символов")
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
