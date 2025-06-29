package com.ukhanov.RealHelpDesk.feature.portalmanager.dto;

import jakarta.validation.constraints.NotBlank;

public class CreatePortalRequest {

    @NotBlank(message = "Название портала не может быть пустым")
    private String name;

    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
