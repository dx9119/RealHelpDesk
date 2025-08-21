package com.ukhanov.realhelpdesk.feature.usermanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NewPasswdRequest {
    @NotBlank(message = "Поле Пароль обязательно для заполнения")
    @Size(min = 8, message = "Пароль должен быть не менее 8 символов")
    private String password;

    public NewPasswdRequest(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
