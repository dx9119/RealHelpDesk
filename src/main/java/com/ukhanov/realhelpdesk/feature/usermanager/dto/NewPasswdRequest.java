package com.ukhanov.realhelpdesk.feature.usermanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewPasswdRequest(@NotBlank(message = "Поле Пароль обязательно для заполнения")
                                @Size(min = 8, message = "Пароль должен быть не менее 8 символов") String password) {
}
