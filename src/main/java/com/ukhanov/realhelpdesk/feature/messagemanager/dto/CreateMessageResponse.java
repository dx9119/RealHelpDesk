package com.ukhanov.realhelpdesk.feature.messagemanager.dto;

public class CreateMessageResponse {

    private Long id;

    public CreateMessageResponse(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
