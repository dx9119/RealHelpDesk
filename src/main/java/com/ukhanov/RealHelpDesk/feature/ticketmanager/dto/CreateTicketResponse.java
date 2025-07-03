package com.ukhanov.RealHelpDesk.feature.ticketmanager.dto;

public class CreateTicketResponse {
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public CreateTicketResponse(String message) {
        this.message = message;
    }
}
