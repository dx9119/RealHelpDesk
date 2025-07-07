package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

public class CreatePortalResponse {
    public CreatePortalResponse(String message) {
        this.message = message;
    }

    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
