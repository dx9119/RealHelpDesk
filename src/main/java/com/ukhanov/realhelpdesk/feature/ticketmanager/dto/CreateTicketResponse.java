package com.ukhanov.realhelpdesk.feature.ticketmanager.dto;

public class CreateTicketResponse {

    private Long id;

    public CreateTicketResponse(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
