package com.ukhanov.RealHelpDesk.feature.messagemanager.dto;

public class CreateMessageResponse {
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public CreateMessageResponse(String message) {
        this.message = message;
    }
}
