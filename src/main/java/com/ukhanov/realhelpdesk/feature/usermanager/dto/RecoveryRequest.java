package com.ukhanov.realhelpdesk.feature.usermanager.dto;

import com.ukhanov.realhelpdesk.core.annotation.NoHtml;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


public class RecoveryRequest {

    @NotNull
    @NoHtml
    @NotBlank(message = "Поле Email обязательно для заполнения")
    @Email(message = "Некорректный формат Email")
    private String email;

    public RecoveryRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
