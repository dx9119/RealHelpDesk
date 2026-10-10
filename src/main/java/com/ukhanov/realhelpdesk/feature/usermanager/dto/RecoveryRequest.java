package com.ukhanov.realhelpdesk.feature.usermanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.ukhanov.realhelpdesk.core.annotation.NoHtml;

public record RecoveryRequest(@NotNull @NoHtml @NotBlank(message = "Поле Email обязательно для заполнения")
                               @Email(message = "Некорректный формат Email") String email) {
}
