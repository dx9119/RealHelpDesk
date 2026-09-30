package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

public class CreatePortalResponse {

    private Long id;

    public CreatePortalResponse(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
