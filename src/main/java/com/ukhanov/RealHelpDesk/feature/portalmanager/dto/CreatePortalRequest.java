package com.ukhanov.RealHelpDesk.feature.portalmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreatePortalRequest {

    @NotBlank(message = "Название портала не может быть пустым")
    @Size(max = 255, message = "Название портала не может превышать 255 символов")
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
