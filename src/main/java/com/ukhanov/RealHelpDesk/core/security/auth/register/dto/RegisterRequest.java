package com.ukhanov.RealHelpDesk.core.security.auth.register.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank(message = "Поле имени обязательно для заполнения")
    @Size(min = 2, max = 50, message = "Имя должно быть не менее 2 символов и не более 50")
    private String firstName;
    @NotBlank(message = "Поле фамилии обязательно для заполнения")
    @Size(min = 2, max = 50, message = "Фамилия должна быть не менее 2 символов и не более 50")
    private String lastName;

    @NotBlank(message = "Поле Email обязательно для заполнения")
    @Email(message = "Некорректный формат Email")
    private String email;

    @NotBlank(message = "Поле Пароль обязательно для заполнения")
    @Size(min = 8, message = "Пароль должен быть не менее 8 символов")
    private String password;

    public RegisterRequest(String firstName, String lastName, String email, String passwordHash) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.password = passwordHash;
    }

    public RegisterRequest() {

    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}
