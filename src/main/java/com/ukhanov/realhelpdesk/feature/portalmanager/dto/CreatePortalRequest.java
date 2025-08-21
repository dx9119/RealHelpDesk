package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import com.ukhanov.realhelpdesk.core.annotation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreatePortalRequest {

    @NotBlank(message = "Название портала не может быть пустым")
    @Size(max = 255, message = "Название портала не может превышать 255 символов")
    @NoHtml
    private String name;

    @Size(max = 2500, message = "Описание не может превышать 2500 символов")
    @NoHtml
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
