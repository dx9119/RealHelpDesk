package com.ukhanov.RealHelpDesk.core.security.auth.login.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "Поле Email обязательно для заполнения")
    @Email(message = "Некорректный формат Email")
    private String email;

    @NotBlank(message = "Поле Пароль обязательно для заполнения")
    @Size(min = 8, message = "Пароль должен быть не менее 8 символов")
    private String password;

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}
